package com.borjaglez.shop.catalog.application.query;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.catalog.application.query.ProductViews.Availability;
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
import com.borjaglez.specrepository.core.GroupedRow;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.jpa.SpecificationExecutableQuery;

/**
 * Answers the public read side of the catalog. Every read uses specification-repository; the stock
 * shown on a product page comes from the inventory service, asked over RabbitMQ.
 */
@QueryHandler
public class CatalogQueryHandler {

  private static final int MAX_FACET_VALUES = 20;

  private final ProductRepository products;
  private final CategoryRepository categories;
  private final StockLevelsGateway stock;
  private final TransactionTemplate readOnly;

  public CatalogQueryHandler(
      ProductRepository products,
      CategoryRepository categories,
      StockLevelsGateway stock,
      PlatformTransactionManager transactionManager) {
    this.products = products;
    this.categories = categories;
    this.stock = stock;
    this.readOnly = new TransactionTemplate(transactionManager);
    this.readOnly.setReadOnly(true);
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public Page<ProductCard> search(SearchProductsQuery query) {
    return onSale(query.getPlan())
        .leftFetch("seller")
        .findAll(query.getPageable())
        .map(ProductViews::card);
  }

  /**
   * The page is read in its own short transaction and the stock asked afterwards, so no database
   * connection waits for the inventory's answer.
   */
  @HandleQuery
  public ProductDetail product(GetProductQuery query) {
    ProductDetail detail = readOnly.execute(status -> page(query));
    if (detail.status() != ProductStatus.ACTIVE) {
      return detail;
    }
    Availability availability =
        stock
            .available(List.of(detail.id()))
            .map(units -> Availability.of(units.getOrDefault(detail.id(), 0)))
            .orElseGet(Availability::unknown);
    return detail.withAvailability(availability);
  }

  private ProductDetail page(GetProductQuery query) {
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
            onSale(QueryPlans.without(client, "categories.slug")),
            "categories.slug",
            "categories.name"),
        countBy(onSale(QueryPlans.without(client, "seller.id")), "seller.id", "seller.displayName"),
        countBy(onSale(QueryPlans.without(client, "tags")), "tags", null),
        priceRange(onSale(QueryPlans.without(client, "price.amount"))));
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public List<CategoryView> categories(ListCategoriesQuery query) {
    Map<String, Long> counts =
        countBy(
                products.query().where("status", Operators.EQUALS, ProductStatus.ACTIVE),
                "categories.slug",
                "categories.name")
            .stream()
            .collect(Collectors.toMap(FacetValue::value, FacetValue::count));
    return categories.query().sort(Sort.by("name")).findAll().stream()
        .map(c -> new CategoryView(c.getSlug(), c.getName(), counts.getOrDefault(c.getSlug(), 0L)))
        .toList();
  }

  /**
   * The client's filters, restricted to products on sale. The status is a server condition: it is
   * not checked against the client's whitelist and a client {@code orFilter} cannot widen it, so a
   * filter on {@code status} can narrow the results but never reveal drafts.
   */
  private SpecificationExecutableQuery<Product> onSale(QueryPlan<Product> clientPlan) {
    return products.query(clientPlan).where("status", Operators.EQUALS, ProductStatus.ACTIVE);
  }

  /**
   * Counts distinct products per value of {@code valueField}: joining categories or tags multiplies
   * the rows of a product.
   */
  private static List<FacetValue> countBy(
      SpecificationExecutableQuery<Product> query, String valueField, String labelField) {
    String[] groupBy =
        labelField == null ? new String[] {valueField} : new String[] {valueField, labelField};
    List<GroupedRow> rows =
        query.groupBy(groupBy).select(groupBy).countDistinctAs("total", "id").findRows();
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

  private static PriceRange priceRange(SpecificationExecutableQuery<Product> query) {
    return query
        .minAs("min", "price.amount")
        .maxAs("max", "price.amount")
        .findRow()
        .filter(r -> r.get("min") != null)
        .map(r -> new PriceRange((BigDecimal) r.get("min"), (BigDecimal) r.get("max")))
        .orElseGet(() -> new PriceRange(null, null));
  }

  private static long toLong(Object value) {
    return ((Number) Objects.requireNonNull(value, "count must not be null")).longValue();
  }
}
