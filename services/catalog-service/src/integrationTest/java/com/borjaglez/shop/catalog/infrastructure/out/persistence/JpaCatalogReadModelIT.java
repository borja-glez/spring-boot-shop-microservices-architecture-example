package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAttribute;

import com.borjaglez.shop.catalog.application.port.in.CatalogFacets;
import com.borjaglez.shop.catalog.application.port.in.CategoryRef;
import com.borjaglez.shop.catalog.application.port.in.CategoryView;
import com.borjaglez.shop.catalog.application.port.in.FacetValue;
import com.borjaglez.shop.catalog.application.port.in.PriceRange;
import com.borjaglez.shop.catalog.application.port.in.ProductCard;
import com.borjaglez.shop.catalog.application.port.in.ProductDetail;
import com.borjaglez.shop.catalog.application.port.in.SellerSummary;
import com.borjaglez.shop.catalog.application.port.out.CatalogReadModel;
import com.borjaglez.shop.catalog.domain.ProductStatus;
import com.borjaglez.shop.kernel.query.ClientFilter;
import com.borjaglez.shop.kernel.query.Page;
import com.borjaglez.shop.kernel.query.PageRequest;
import com.borjaglez.shop.support.web.problem.MappedProblem;
import com.borjaglez.shop.support.web.problem.SpecificationProblemMapper;

/**
 * The public read side over the seeded catalog (V2__catalog_seed.sql): 72 products, 62 of them on
 * sale. Filters arrive as the shopper typed them and are checked against the whitelist and the
 * {@code specrepository.http.*} limits of the application; every read sees products on sale only.
 * Tests of other classes sharing the database commit drafts only, which no read here sees.
 */
@CatalogPersistenceTest
class JpaCatalogReadModelIT {

  private static final PageRequest ALL = new PageRequest(0, 100);
  private static final String CAF_001 = "cafe-de-colombia-en-grano-1-kg-caf-001";

  @Autowired CatalogReadModel catalog;
  @Autowired EntityManagerFactory entityManagerFactory;

  private static ClientFilter filters(String... filters) {
    return new ClientFilter(List.of(filters), List.of(), List.of());
  }

  private static ClientFilter sorts(String... sorts) {
    return new ClientFilter(List.of(), List.of(), List.of(sorts));
  }

  /** The SKUs the search finds, ordered by SKU. */
  private List<String> skus(List<String> filters, List<String> orFilters) {
    return catalog
        .search(new ClientFilter(filters, orFilters, List.of("sku")), ALL)
        .content()
        .stream()
        .map(ProductCard::sku)
        .toList();
  }

  private List<String> skus(String... filters) {
    return skus(List.of(filters), List.of());
  }

  private long total(String... filters) {
    return catalog.search(filters(filters), new PageRequest(0, 1)).totalElements();
  }

  private ProductCard card(String sku) {
    return catalog.search(filters("sku:eq:" + sku), ALL).content().getFirst();
  }

  // --- search ---

  @Test
  void searchOnlyReturnsProductsOnSale() {
    List<ProductCard> found =
        catalog
            .search(
                new ClientFilter(
                    List.of("categories.slug:eq:cafe-e-infusiones"), List.of(), List.of("sku")),
                ALL)
            .content();

    assertThat(found)
        .extracting(ProductCard::sku)
        .containsExactly(
            "CAF-001", "CAF-002", "CAF-003", "CAF-004", "CAF-005", "CAF-006", "ELE-005", "LIB-004");
    assertThat(found).allMatch(card -> card.status() == ProductStatus.ACTIVE);
  }

  static Stream<Arguments> everyAllowedOperator() {
    return Stream.of(
        arguments("eq", List.of("sku:eq:CAF-001"), List.of("CAF-001")),
        arguments(
            "neq",
            List.of("categories.slug:eq:cafe-e-infusiones", "seller.id:neq:seller-ana"),
            List.of("CAF-003", "CAF-006", "ELE-005", "LIB-004")),
        arguments("contains", List.of("name:contains:cafetera"), List.of("CAF-003", "ELE-005")),
        arguments(
            "notcontains",
            List.of("categories.slug:eq:conservas", "name:notcontains:cantabrico"),
            List.of("CON-001", "CON-002", "CON-003", "CON-005")),
        arguments(
            "startswith",
            List.of("name:startswith:cafe"),
            List.of("CAF-001", "CAF-002", "CAF-003", "CAF-004", "ELE-005")),
        arguments(
            "endswith", List.of("name:endswith:500 g"), List.of("CAF-002", "DES-002", "DUL-006")),
        arguments("gt", List.of("price.amount:gt:349"), List.of("ELE-003")),
        arguments("gte", List.of("price.amount:gte:349"), List.of("ELE-003", "ELE-005")),
        arguments("lt", List.of("price.amount:lt:3.10"), List.of("DES-005")),
        arguments("lte", List.of("price.amount:lte:3.10"), List.of("DES-002", "DES-005")),
        arguments(
            "between",
            List.of("price.amount:between:30|50"),
            List.of("ACE-001", "ELE-002", "ELE-004", "ELE-006", "HOG-001", "HOG-005", "NIN-004")),
        arguments("in", List.of("sku:in:CAF-001|VIN-002|CAF-007"), List.of("CAF-001", "VIN-002")),
        arguments(
            "notin",
            List.of("categories.slug:eq:conservas", "seller.id:notin:seller-fermin"),
            List.of("CON-002", "CON-005")),
        arguments("isnull", List.of("publishedAt:isnull"), List.of()),
        arguments(
            "isnotnull",
            List.of("publishedAt:isnotnull", "categories.slug:eq:vinos"),
            List.of("VIN-001", "VIN-002", "VIN-003", "VIN-004")),
        arguments(
            "isempty",
            List.of("tags:isempty"),
            List.of("DES-006", "EMB-003", "NIN-004", "VIN-003")),
        arguments(
            "isnotempty",
            List.of("tags:isnotempty", "categories.slug:eq:vinos"),
            List.of("VIN-001", "VIN-002", "VIN-004")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  void everyAllowedOperator(String operator, List<String> filters, List<String> expected) {
    assertThat(skus(filters, List.of())).containsExactlyElementsOf(expected);
  }

  @Test
  void operatorsOverTheWholeCatalogCountProductsOnSaleOnly() {
    assertThat(total("price.amount:between:5|20")).isEqualTo(33);
    assertThat(total("publishedAt:isnotnull")).isEqualTo(62);
    assertThat(total("tags:isnotempty")).isEqualTo(58);
    assertThat(total("status:eq:ACTIVE")).isEqualTo(62);
  }

  @Test
  void nameAndDescriptionIgnoreCaseAndAccentsAndNoOtherFieldDoes() {
    assertThat(skus("name:contains:CAFETERA")).containsExactly("CAF-003", "ELE-005");
    assertThat(skus("name:contains:café"))
        .containsExactly("CAF-001", "CAF-002", "CAF-003", "CAF-004", "ELE-005", "LIB-004");
    assertThat(skus("description:contains:CITRICOS")).containsExactly("CAF-001");
    assertThat(skus("name:contains:D'OLIVA DE L'EMPORDA")).containsExactly("ACE-004");

    assertThat(skus("sku:startswith:caf")).isEmpty();
    assertThat(skus("sku:startswith:CAF")).hasSize(6);
    assertThat(skus("seller.city:eq:madrid")).isEmpty();
    assertThat(skus("seller.city:eq:Madrid"))
        .containsExactly("CAF-001", "CAF-002", "CAF-004", "CAF-005", "DUL-004");
  }

  @Test
  void alternativesAreCombinedWithOrAndTheFiltersWithAnd() {
    List<String> alternatives = List.of("categories.slug:eq:vinos;seller.id:eq:seller-diego");

    assertThat(skus(List.of(), alternatives))
        .containsExactly(
            "ELE-001", "ELE-002", "ELE-003", "ELE-004", "ELE-005", "ELE-006", "ELE-007", "VIN-001",
            "VIN-002", "VIN-003", "VIN-004");
    assertThat(skus(List.of("price.amount:lt:10"), alternatives))
        .containsExactly("VIN-003", "VIN-004");
  }

  @Test
  void clientFiltersCannotRevealProductsNotOnSale() {
    assertThat(total("status:eq:DRAFT")).isZero();
    assertThat(total("status:eq:DISCONTINUED")).isZero();
    assertThat(skus(List.of(), List.of("status:eq:DRAFT;sku:eq:CAF-001")))
        .containsExactly("CAF-001");
  }

  @Test
  void theClientSortsByTheSortableFields() {
    List<ProductCard> cheapestFirst = catalog.search(sorts("price.amount,asc"), ALL).content();
    List<ProductCard> dearestFirst = catalog.search(sorts("price.amount,desc"), ALL).content();

    assertThat(cheapestFirst).hasSize(62);
    assertThat(cheapestFirst.getFirst().sku()).isEqualTo("DES-005");
    assertThat(cheapestFirst.getLast().sku()).isEqualTo("ELE-003");
    assertThat(dearestFirst.getFirst().sku()).isEqualTo("ELE-003");
    assertThat(catalog.search(sorts("publishedAt"), ALL).content())
        .isSortedAccordingTo(Comparator.comparing(ProductCard::publishedAt));
  }

  @Test
  void withoutASortTheNewestComeFirstWithAStableTieBreaker() {
    List<ProductCard> found = catalog.search(ClientFilter.none(), ALL).content();

    assertThat(found).hasSize(62);
    assertThat(found)
        .isSortedAccordingTo(
            Comparator.comparing(ProductCard::publishedAt, Comparator.reverseOrder())
                // PostgreSQL orders uuids byte by byte, as their text form; UUID.compareTo does
                // not.
                .thenComparing(card -> card.id().toString()));
  }

  @Test
  void searchPagesAndCountsTotals() {
    Page<ProductCard> page =
        catalog.search(
            new ClientFilter(
                List.of("categories.slug:eq:cafe-e-infusiones"), List.of(), List.of("sku")),
            new PageRequest(1, 3));

    assertThat(page.content())
        .extracting(ProductCard::sku)
        .containsExactly("CAF-004", "CAF-005", "CAF-006");
    assertThat(page.page()).isEqualTo(1);
    assertThat(page.size()).isEqualTo(3);
    assertThat(page.totalElements()).isEqualTo(8);
    assertThat(page.totalPages()).isEqualTo(3);
  }

  @Test
  void aPageBeyondTheLastIsEmptyAndKeepsTheTotal() {
    Page<ProductCard> page = catalog.search(filters("sku:eq:CAF-001"), new PageRequest(5, 10));

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isEqualTo(1);
  }

  @Test
  void productsMatchingSeveralValuesOfAToManyFieldAppearOnce() {
    Page<ProductCard> tagged = catalog.search(filters("tags:in:ecologico|te"), ALL);
    Page<ProductCard> inCategories =
        catalog.search(
            filters(
                "categories.slug:in:cafe-e-infusiones|despensa|hogar|aceites-y-vinagres|conservas"),
            ALL);

    assertThat(tagged.content()).extracting(ProductCard::sku).doesNotHaveDuplicates();
    assertThat(tagged.totalElements()).isEqualTo(8);
    assertThat(inCategories.content()).extracting(ProductCard::sku).doesNotHaveDuplicates();
    assertThat(inCategories.totalElements()).isEqualTo(inCategories.content().size());
  }

  @Test
  void cardsCarryThePublicDataOfTheProductAndItsSeller() {
    ProductCard card = card("CAF-001");

    assertThat(card.slug()).isEqualTo(CAF_001);
    assertThat(card.name()).isEqualTo("Café de Colombia en grano 1 kg");
    assertThat(card.price()).isEqualByComparingTo("18.90");
    assertThat(card.currency()).isEqualTo("EUR");
    assertThat(card.status()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(card.seller())
        .isEqualTo(new SellerSummary("seller-ana", "Tostadores Ana", "Madrid"));
    assertThat(card.categories()).containsExactly("cafe-e-infusiones");
    assertThat(card.tags()).containsExactly("comercio-justo", "ecologico");
    assertThat(card.publishedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(card("ELE-005").categories())
        .containsExactly("cafe-e-infusiones", "electronica", "hogar");
  }

  /**
   * At most the count, the page with the seller of each product, and one batch each for the
   * categories and the tags, however many sellers the page shows and even when a filter joins them:
   * reading the sellers costs no query of their own.
   */
  @Test
  void aPageTakesAtMostFourStatementsWhateverItsSellers() {
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    try {
      statistics.clear();

      List<ProductCard> found =
          catalog
              .search(
                  filters(
                      "categories.slug:eq:cafe-e-infusiones",
                      "seller.city:in:Madrid|Valencia|Bilbao|A Coruña|Zaragoza"),
                  ALL)
              .content();

      assertThat(found).hasSize(8);
      assertThat(found)
          .extracting(card -> card.seller().displayName())
          .containsOnly(
              "Tostadores Ana",
              "Carmen Hogar",
              "Diego Electrónica",
              "Librería Elena",
              "Ultramarinos Fermín");
      assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    } finally {
      statistics.setStatisticsEnabled(false);
    }
  }

  // --- filters the read side rejects ---

  static Stream<Arguments> rejectedFilters() {
    String tooManySellers =
        IntStream.rangeClosed(1, 51).mapToObj(i -> "seller-" + i).collect(Collectors.joining("|"));
    return Stream.of(
        arguments(
            "a field outside the whitelist",
            filters("seller.email:startswith:ana"),
            "seller.email",
            "seller.email"),
        arguments(
            "an alternative outside the whitelist",
            new ClientFilter(List.of(), List.of("sku:eq:CAF-001;seller.email:eq:x"), List.of()),
            "seller.email",
            "seller.email"),
        arguments(
            "a field the products do not have",
            filters("nonexistent:eq:x"),
            "nonexistent",
            "nonexistent"),
        arguments("a sort outside the whitelist", sorts("id"), "id", "sorting"),
        arguments("a private sort", sorts("seller.email,asc"), "seller.email", "sorting"),
        arguments(
            "a value of the wrong type",
            filters("price.amount:gte:abc"),
            "price.amount",
            "price.amount"),
        arguments("an unknown status", filters("status:eq:SOLD_OUT"), "status", "status"),
        arguments("an operator not allowed", filters("name:like:cafe"), null, "'like'"),
        arguments(
            "a value above the catalog limit",
            filters("name:contains:" + "a".repeat(101)),
            null,
            "max 100 characters"),
        arguments(
            "more values than the shop allows",
            filters("seller.id:in:" + tooManySellers),
            null,
            "too many values (max 50)"),
        arguments("a filter without a value", filters("name:contains"), null, "value is required"),
        arguments("a sort in no direction", sorts("name,up"), null, "invalid sort direction"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  void rejectedFilters(String description, ClientFilter filter, String field, String detail) {
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    MappedProblem problem;
    try {
      statistics.clear();
      problem = problemOf(catchThrowable(() -> catalog.search(filter, ALL)));
      // Rejected while the query is built: no SQL reaches the database.
      assertThat(statistics.getPrepareStatementCount()).isZero();
    } finally {
      statistics.setStatisticsEnabled(false);
    }

    assertThat(problem.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(problem.code()).isEqualTo("invalid-filter");
    assertThat(problem.properties().get("field")).isEqualTo(field);
    assertThat(problem.detail()).contains(detail);
  }

  @Test
  void facetsRejectTheFiltersTheSearchRejects() {
    MappedProblem problem =
        problemOf(catchThrowable(() -> catalog.facets(filters("seller.email:eq:x"))));

    assertThat(problem.code()).isEqualTo("invalid-filter");
    assertThat(problem.properties()).containsEntry("field", "seller.email");
  }

  @Test
  void facetsAreNotSorted() {
    MappedProblem problem = problemOf(catchThrowable(() -> catalog.facets(sorts("name"))));

    assertThat(problem.code()).isEqualTo("invalid-filter");
    assertThat(problem.properties()).containsEntry("field", "name");
  }

  /**
   * The problem the web layer answers with: the first exception of the chain the shop's mapper of
   * specification-repository errors recognises, as {@code ProblemDetailsExceptionHandler} looks for
   * it.
   */
  private static MappedProblem problemOf(Throwable thrown) {
    SpecificationProblemMapper mapper = new SpecificationProblemMapper();
    Set<Throwable> seen = new HashSet<>();
    for (Throwable cause = thrown; cause != null && seen.add(cause); cause = cause.getCause()) {
      Optional<MappedProblem> problem = mapper.map(cause);
      if (problem.isPresent()) {
        return problem.get();
      }
    }
    throw new AssertionError("Not a filter error: " + thrown, thrown);
  }

  // --- product page ---

  @Test
  void aProductOnSaleHasAPublicPageWithoutStock() {
    ProductDetail detail = catalog.publicProduct(CAF_001).orElseThrow();

    assertThat(detail.sku()).isEqualTo("CAF-001");
    assertThat(detail.slug()).isEqualTo(CAF_001);
    assertThat(detail.description()).isEqualTo("Tueste medio, notas de caramelo y cítricos.");
    assertThat(detail.price()).isEqualByComparingTo("18.90");
    assertThat(detail.status()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(detail.seller())
        .isEqualTo(new SellerSummary("seller-ana", "Tostadores Ana", "Madrid"));
    assertThat(detail.categories())
        .containsExactly(new CategoryRef("cafe-e-infusiones", "Café e infusiones"));
    assertThat(detail.tags()).containsExactly("comercio-justo", "ecologico");
    assertThat(detail.publishedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(detail.updatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(detail.version()).isZero();
    assertThat(detail.availability()).isNull();
  }

  @Test
  void theCategoriesOfAPageAreOrderedBySlug() {
    String slug = card("ELE-005").slug();

    assertThat(catalog.publicProduct(slug).orElseThrow().categories())
        .containsExactly(
            new CategoryRef("cafe-e-infusiones", "Café e infusiones"),
            new CategoryRef("electronica", "Electrónica"),
            new CategoryRef("hogar", "Hogar"));
  }

  @Test
  void aDiscontinuedProductKeepsItsPublicPage() {
    assertThat(catalog.publicProduct("rooibos-con-vainilla-caf-008"))
        .hasValueSatisfying(
            detail -> assertThat(detail.status()).isEqualTo(ProductStatus.DISCONTINUED));
  }

  @Test
  void draftsAndUnknownSlugsHaveNoPublicPage() {
    assertThat(catalog.publicProduct("cafe-de-guatemala-huehuetenango-caf-007")).isEmpty();
    assertThat(catalog.publicProduct("no-existe")).isEmpty();
  }

  // --- facets ---

  private static ClientFilter inCoffee() {
    return filters("categories.slug:eq:cafe-e-infusiones");
  }

  private static long countOf(List<FacetValue> values, String value) {
    return values.stream()
        .filter(facet -> facet.value().equals(value))
        .findFirst()
        .map(FacetValue::count)
        .orElse(0L);
  }

  @Test
  void facetsCountTheProductsTheSearchFinds() {
    CatalogFacets facets = catalog.facets(inCoffee());

    assertThat(facets.categories())
        .contains(new FacetValue("cafe-e-infusiones", "Café e infusiones", 8));
    assertThat(facets.sellers())
        .containsExactly(
            new FacetValue("seller-ana", "Tostadores Ana", 4),
            new FacetValue("seller-carmen", "Carmen Hogar", 1),
            new FacetValue("seller-diego", "Diego Electrónica", 1),
            new FacetValue("seller-elena", "Librería Elena", 1),
            new FacetValue("seller-fermin", "Ultramarinos Fermín", 1));
    assertThat(countOf(facets.tags(), "ecologico")).isEqualTo(3);
    assertThat(facets.tags()).contains(new FacetValue("te", "te", 2));
    assertThat(facets.price().min()).isEqualByComparingTo("3.20");
    assertThat(facets.price().max()).isEqualByComparingTo("349.00");
  }

  @Test
  void eachFacetIgnoresTheFilterOnItsOwnField() {
    CatalogFacets facets = catalog.facets(filters("seller.id:in:seller-ana"));

    // Other sellers stay selectable, with the counts they would add.
    assertThat(facets.sellers())
        .extracting(FacetValue::value)
        .contains("seller-ana", "seller-carmen", "seller-diego");
    // The other facets follow the seller filter.
    assertThat(facets.categories())
        .extracting(FacetValue::value)
        .containsOnly("cafe-e-infusiones", "dulces");
  }

  @Test
  void facetsCountEachProductOnceUnderToManyFilters() {
    CatalogFacets facets = catalog.facets(filters("tags:in:ecologico|te"));

    assertThat(countOf(facets.categories(), "cafe-e-infusiones")).isEqualTo(4);
  }

  @Test
  void facetsKeepTheTwentyCommonestValuesFirst() {
    List<FacetValue> tags = catalog.facets(ClientFilter.none()).tags();

    assertThat(tags).hasSize(20);
    assertThat(tags.getFirst())
        .isEqualTo(new FacetValue("denominacion-de-origen", "denominacion-de-origen", 11));
    assertThat(tags)
        .isSortedAccordingTo(
            Comparator.comparingLong(FacetValue::count)
                .reversed()
                .thenComparing(FacetValue::label));
  }

  @Test
  void thePriceRangeCoversTheWholeCatalogWithoutFilters() {
    PriceRange price = catalog.facets(ClientFilter.none()).price();

    assertThat(price.min()).isEqualByComparingTo("2.95");
    assertThat(price.max()).isEqualByComparingTo(new BigDecimal("899.00"));
  }

  @Test
  void aSearchThatFindsNothingHasEmptyFacets() {
    assertThat(catalog.facets(filters("sku:eq:NOPE-000")))
        .isEqualTo(new CatalogFacets(List.of(), List.of(), List.of(), new PriceRange(null, null)));
  }

  // --- categories ---

  @Test
  void everyCategoryIsListedByNameWithItsProductsOnSale() {
    List<CategoryView> categories = catalog.categories();

    assertThat(categories)
        .extracting(CategoryView::slug)
        .containsExactly(
            "aceites-y-vinagres",
            "cafe-e-infusiones",
            "conservas",
            "despensa",
            "dulces",
            "electronica",
            "embutidos-y-quesos",
            "hogar",
            "libros",
            "ninos",
            "textil",
            "vinos");
    assertThat(categories)
        .contains(
            new CategoryView("cafe-e-infusiones", "Café e infusiones", 8),
            new CategoryView("vinos", "Vinos", 4),
            new CategoryView("electronica", "Electrónica", 7));
  }

  // --- transactions ---

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void eachReadRunsInATransactionOfItsOwn() {
    assertThat(card("ELE-005").categories()).hasSize(3);
    assertThat(catalog.publicProduct(CAF_001)).isPresent();
    assertThat(catalog.facets(inCoffee()).categories()).isNotEmpty();
    assertThat(catalog.categories()).hasSize(12);
  }

  @Test
  void everyReadIsReadOnlyAndGivesUpAfterFiveSeconds() {
    AnnotationTransactionAttributeSource transactions = new AnnotationTransactionAttributeSource();

    for (Method read : CatalogReadModel.class.getMethods()) {
      TransactionAttribute transaction =
          transactions.getTransactionAttribute(read, JpaCatalogReadModel.class);
      assertThat(transaction).as(read.getName()).isNotNull();
      assertThat(transaction.isReadOnly()).as(read.getName()).isTrue();
      assertThat(transaction.getTimeout()).as(read.getName()).isEqualTo(5);
    }
  }
}
