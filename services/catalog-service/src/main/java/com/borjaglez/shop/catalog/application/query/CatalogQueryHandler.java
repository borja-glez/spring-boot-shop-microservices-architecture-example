package com.borjaglez.shop.catalog.application.query;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.catalog.application.query.ProductViews.CatalogFacets;
import com.borjaglez.shop.catalog.application.query.ProductViews.CategoryView;
import com.borjaglez.shop.catalog.application.query.ProductViews.FacetValue;
import com.borjaglez.shop.catalog.application.query.ProductViews.PriceRange;
import com.borjaglez.shop.catalog.application.query.ProductViews.ProductCard;
import com.borjaglez.shop.catalog.application.query.ProductViews.ProductDetail;
import com.borjaglez.shop.catalog.domain.CategoryRepository;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.ProductRepository;
import com.borjaglez.shop.catalog.domain.ProductStatus;
import com.borjaglez.shop.support.error.NotFoundException;
import com.borjaglez.shop.support.query.QueryPlans;
import com.borjaglez.specrepository.core.AggregateFunction;
import com.borjaglez.specrepository.core.AggregateSelection;
import com.borjaglez.specrepository.core.FieldSelection;
import com.borjaglez.specrepository.core.GroupedRow;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.Selection;
import com.borjaglez.specrepository.core.SpecificationQueryBuilder;

/** Answers the public read side of the catalog. Every read uses specification-repository. */
@QueryHandler
public class CatalogQueryHandler {

  private static final PredicateCondition ON_SALE =
      new PredicateCondition("status", Operators.EQUALS, ProductStatus.ACTIVE, false, false);
  private static final int MAX_FACET_VALUES = 20;
  private static final Set<String> TEXT_FIELDS = Set.of("name", "description");

  private final ProductRepository products;
  private final CategoryRepository categories;

  public CatalogQueryHandler(ProductRepository products, CategoryRepository categories) {
    this.products = products;
    this.categories = categories;
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public Page<ProductCard> search(SearchProductsQuery query) {
    QueryPlan<Product> plan = QueryPlans.fetching(publicPlan(query.getPlan()), "seller");
    return products.findAll(plan, query.getPageable()).map(ProductViews::card);
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public ProductDetail product(GetProductQuery query) {
    return products
        .query()
        .where("slug", Operators.EQUALS, query.getSlug())
        .where("status", Operators.NOT_EQUALS, ProductStatus.DRAFT)
        .leftFetch("seller")
        .findOne()
        .map(ProductViews::detail)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "product-not-found", "No product is published as " + query.getSlug()));
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public CatalogFacets facets(GetCatalogFacetsQuery query) {
    // Each facet ignores its own filter (disjunctive faceting), so shoppers can pick several
    // sellers or tags and see what each extra choice would add.
    QueryPlan<Product> client = query.getPlan();
    return new CatalogFacets(
        countBy(
            publicPlan(QueryPlans.without(client, "categories.slug")),
            "categories.slug",
            "categories.name"),
        countBy(
            publicPlan(QueryPlans.without(client, "seller.id")), "seller.id", "seller.displayName"),
        countBy(publicPlan(QueryPlans.without(client, "tags")), "tags", null),
        priceRange(publicPlan(QueryPlans.without(client, "price.amount"))));
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public List<CategoryView> categories(ListCategoriesQuery query) {
    QueryPlan<Product> onSale =
        QueryPlans.requiring(SpecificationQueryBuilder.forEntity(Product.class).build(), ON_SALE);
    Map<String, Long> counts =
        countBy(onSale, "categories.slug", "categories.name").stream()
            .collect(Collectors.toMap(FacetValue::value, FacetValue::count));
    return categories.query().sort(Sort.by("name")).findAll().stream()
        .map(c -> new CategoryView(c.getSlug(), c.getName(), counts.getOrDefault(c.getSlug(), 0L)))
        .toList();
  }

  /** Client filters, case-insensitive on text, restricted to products on sale. */
  private static QueryPlan<Product> publicPlan(QueryPlan<Product> clientPlan) {
    return QueryPlans.requiring(QueryPlans.ignoringCase(clientPlan, TEXT_FIELDS), ON_SALE);
  }

  private List<FacetValue> countBy(QueryPlan<Product> plan, String valueField, String labelField) {
    List<String> groupBy =
        labelField == null ? List.of(valueField) : List.of(valueField, labelField);
    List<Selection> selections =
        new ArrayList<>(groupBy.stream().map(FieldSelection::new).toList());
    selections.add(new AggregateSelection(AggregateFunction.COUNT_DISTINCT, "id", "total"));
    List<GroupedRow> rows = products.findAllGrouped(QueryPlans.grouping(plan, groupBy, selections));
    Function<GroupedRow, String> label =
        labelField == null
            ? r -> String.valueOf(r.get(valueField))
            : r -> String.valueOf(r.get(labelField));
    return rows.stream()
        .filter(r -> r.get(valueField) != null)
        .map(
            r ->
                new FacetValue(
                    String.valueOf(r.get(valueField)), label.apply(r), toLong(r.get("total"))))
        .sorted(
            Comparator.comparingLong(FacetValue::count).reversed().thenComparing(FacetValue::label))
        .limit(MAX_FACET_VALUES)
        .toList();
  }

  private PriceRange priceRange(QueryPlan<Product> plan) {
    List<GroupedRow> rows =
        products.findAllGrouped(
            QueryPlans.grouping(
                plan,
                List.of(),
                List.of(
                    new AggregateSelection(AggregateFunction.MIN, "price.amount", "min"),
                    new AggregateSelection(AggregateFunction.MAX, "price.amount", "max"))));
    if (rows.isEmpty() || rows.getFirst().get("min") == null) {
      return new PriceRange(null, null);
    }
    return new PriceRange(
        (BigDecimal) rows.getFirst().get("min"), (BigDecimal) rows.getFirst().get("max"));
  }

  private static long toLong(Object value) {
    return ((Number) Objects.requireNonNull(value, "count must not be null")).longValue();
  }
}
