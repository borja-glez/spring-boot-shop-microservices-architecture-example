package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.shop.catalog.application.port.in.CatalogFacets;
import com.borjaglez.shop.catalog.application.port.in.CategoryView;
import com.borjaglez.shop.catalog.application.port.in.FacetValue;
import com.borjaglez.shop.catalog.application.port.in.PriceRange;
import com.borjaglez.shop.catalog.application.port.in.ProductCard;
import com.borjaglez.shop.catalog.application.port.in.ProductDetail;
import com.borjaglez.shop.catalog.application.port.out.CatalogReadModel;
import com.borjaglez.shop.catalog.domain.ProductStatus;
import com.borjaglez.shop.kernel.query.ClientFilter;
import com.borjaglez.shop.kernel.query.Page;
import com.borjaglez.shop.kernel.query.PageRequest;
import com.borjaglez.shop.support.query.QueryPlans;
import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.GroupedRow;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.http.HttpFilterParser;
import com.borjaglez.specrepository.http.HttpFilterParserConfiguration;
import com.borjaglez.specrepository.jpa.SpecificationExecutableQuery;

/**
 * [Hexagonal · adaptador driven] The public read side of the catalog on PostgreSQL, through
 * specification-repository.
 *
 * <p>The shopper's filters are parsed with the HTTP filter syntax of specification-repository and
 * its {@code specrepository.http.*} limits into a client plan that carries the whitelist of {@link
 * ProductFilters}; the repository checks the client part of every plan against it before any SQL
 * runs. The rejections are the library's own exceptions (those raised while a query runs arrive
 * wrapped by the repository proxy), which the web layer answers with 400 {@code invalid-filter}.
 * What the read side adds to the client plan (products on sale only, the seller fetched with each
 * product, the default sort) is server input: the whitelist does not apply to it and a client
 * {@code orFilter} cannot widen it.
 *
 * <p>Public reads take the shopper's filters, so a combination the indexes do not cover must not
 * hold a connection for long: each read runs in a read-only transaction of its own that gives up
 * after {@value #TIMEOUT_SECONDS} seconds, a timeout Spring applies to every query in it.
 */
public class JpaCatalogReadModel implements CatalogReadModel {

  static final int TIMEOUT_SECONDS = 5;

  private static final int MAX_FACET_VALUES = 20;

  /**
   * Newest first when the shopper picks no order. A server sort: {@code id} is not a sortable field
   * of the search, but it breaks ties between products published at the same instant, so a page
   * never repeats or skips a product.
   */
  static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.asc("id"));

  private final ProductJpaRepository products;
  private final CategoryJpaRepository categories;
  private final HttpFilterParserConfiguration syntax;
  private final HttpFilterParser parser;

  public JpaCatalogReadModel(
      ProductJpaRepository products,
      CategoryJpaRepository categories,
      HttpFilterParserConfiguration syntax) {
    this.products = products;
    this.categories = categories;
    this.syntax = syntax;
    this.parser = new HttpFilterParser(syntax);
  }

  @Override
  @Transactional(readOnly = true, timeout = TIMEOUT_SECONDS)
  public Page<ProductCard> search(ClientFilter filter, PageRequest page) {
    org.springframework.data.domain.Page<ProductEntity> found =
        onSale(clientPlan(filter, ProductFilters.SEARCH))
            .leftFetch("seller")
            .sortedByDefault(NEWEST_FIRST)
            .findAll(org.springframework.data.domain.PageRequest.of(page.page(), page.size()));
    return new Page<>(
        found.getContent().stream().map(CatalogViewMapper::card).toList(),
        page.page(),
        page.size(),
        found.getTotalElements());
  }

  @Override
  @Transactional(readOnly = true, timeout = TIMEOUT_SECONDS)
  public CatalogFacets facets(ClientFilter filter) {
    // Each facet ignores its own filter (disjunctive faceting), so shoppers can pick several
    // sellers or tags and see what each extra choice would add.
    QueryPlan<ProductEntity> client = clientPlan(filter, ProductFilters.FACETS);
    return new CatalogFacets(
        countBy(
            onSale(QueryPlans.without(client, "categories.slug")),
            "categories.slug",
            "categories.name"),
        countBy(onSale(QueryPlans.without(client, "seller.id")), "seller.id", "seller.displayName"),
        countBy(onSale(QueryPlans.without(client, "tags")), "tags", null),
        priceRange(onSale(QueryPlans.without(client, "price.amount"))));
  }

  @Override
  @Transactional(readOnly = true, timeout = TIMEOUT_SECONDS)
  public Optional<ProductDetail> publicProduct(String slug) {
    return products
        .query()
        .where("slug", Operators.EQUALS, slug)
        .where("status", Operators.NOT_EQUALS, ProductStatus.DRAFT)
        .leftFetch("seller")
        .findOne()
        .map(CatalogViewMapper::detail);
  }

  @Override
  @Transactional(readOnly = true, timeout = TIMEOUT_SECONDS)
  public List<CategoryView> categories() {
    Map<String, Long> counts =
        countBy(
                products.query().where("status", Operators.EQUALS, ProductStatus.ACTIVE),
                "categories.slug",
                "categories.name")
            .stream()
            .collect(Collectors.toMap(FacetValue::value, FacetValue::count));
    return categories.query().sort(Sort.by("name")).findAll().stream()
        .map(
            category ->
                new CategoryView(
                    category.getSlug(),
                    category.getName(),
                    counts.getOrDefault(category.getSlug(), 0L)))
        .toList();
  }

  /**
   * The shopper's filters and sorts as a client plan of specification-repository, with {@code
   * policy} as its whitelist: a field outside it fails with a {@link
   * com.borjaglez.specrepository.core.DisallowedFieldException} when the plan is run.
   *
   * @throws com.borjaglez.specrepository.http.HttpFilterSyntaxException if a filter or sort is
   *     malformed or above the limits of the syntax
   * @throws com.borjaglez.specrepository.http.HttpUnknownOperatorException if an operator is not
   *     allowed
   */
  private QueryPlan<ProductEntity> clientPlan(ClientFilter filter, AllowedFieldsPolicy policy) {
    Map<String, List<String>> params =
        Map.of(
            syntax.filterParam(), filter.filters(),
            syntax.orFilterParam(), filter.orFilters(),
            syntax.sortParam(), filter.sorts());
    return parser.toQueryPlan(
        ProductEntity.class, params, policy, Set.copyOf(ProductFilters.CASE_INSENSITIVE_FIELDS));
  }

  /**
   * The client's filters, restricted to products on sale. The status is a server condition: it is
   * not checked against the whitelist and a client {@code orFilter} cannot widen it, so a filter on
   * {@code status} can narrow the results but never reveal drafts.
   */
  private SpecificationExecutableQuery<ProductEntity> onSale(QueryPlan<ProductEntity> clientPlan) {
    return products.query(clientPlan).where("status", Operators.EQUALS, ProductStatus.ACTIVE);
  }

  /**
   * Counts distinct products per value of {@code valueField}: joining categories or tags multiplies
   * the rows of a product. The commonest values come first, by count and then by label.
   */
  private static List<FacetValue> countBy(
      SpecificationExecutableQuery<ProductEntity> query, String valueField, String labelField) {
    String[] groupBy =
        labelField == null ? new String[] {valueField} : new String[] {valueField, labelField};
    List<GroupedRow> rows =
        query.groupBy(groupBy).select(groupBy).countDistinctAs("total", "id").findRows();
    Function<GroupedRow, String> label =
        labelField == null
            ? row -> String.valueOf(row.get(valueField))
            : row -> String.valueOf(row.get(labelField));
    return rows.stream()
        .filter(row -> row.get(valueField) != null)
        .map(
            row ->
                new FacetValue(
                    String.valueOf(row.get(valueField)),
                    label.apply(row),
                    toLong(row.get("total"))))
        .sorted(
            Comparator.comparingLong(FacetValue::count).reversed().thenComparing(FacetValue::label))
        .limit(MAX_FACET_VALUES)
        .toList();
  }

  private static PriceRange priceRange(SpecificationExecutableQuery<ProductEntity> query) {
    return query
        .minAs("min", "price.amount")
        .maxAs("max", "price.amount")
        .findRow()
        .filter(row -> row.get("min") != null)
        .map(row -> new PriceRange((BigDecimal) row.get("min"), (BigDecimal) row.get("max")))
        .orElseGet(() -> new PriceRange(null, null));
  }

  private static long toLong(Object value) {
    return ((Number) Objects.requireNonNull(value, "count must not be null")).longValue();
  }
}
