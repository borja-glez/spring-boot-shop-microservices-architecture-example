package com.borjaglez.shop.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.catalog.FakeStockLevels;
import com.borjaglez.shop.catalog.application.query.GetCatalogFacetsQuery;
import com.borjaglez.shop.catalog.application.query.GetProductQuery;
import com.borjaglez.shop.catalog.application.query.ListCategoriesQuery;
import com.borjaglez.shop.catalog.application.query.ProductViews.Availability;
import com.borjaglez.shop.catalog.application.query.ProductViews.CatalogFacets;
import com.borjaglez.shop.catalog.application.query.ProductViews.CategoryView;
import com.borjaglez.shop.catalog.application.query.ProductViews.FacetValue;
import com.borjaglez.shop.catalog.application.query.ProductViews.ProductCard;
import com.borjaglez.shop.catalog.application.query.ProductViews.ProductDetail;
import com.borjaglez.shop.catalog.application.query.ProductViews.StockStatus;
import com.borjaglez.shop.catalog.application.query.SearchProductsQuery;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.ProductStatus;
import com.borjaglez.shop.support.error.NotFoundException;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.SpecificationQueryBuilder;

/** Read side over the seeded catalog, through the real query bus. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  FakeStockLevels.class
})
class CatalogQueriesIT {

  @Autowired QueryBus queries;
  @Autowired FakeStockLevels inventory;

  private static QueryPlan<Product> all() {
    return SpecificationQueryBuilder.forEntity(Product.class).build();
  }

  private static QueryPlan<Product> inCoffee() {
    return SpecificationQueryBuilder.forEntity(Product.class)
        .where("categories.slug", Operators.EQUALS, "cafe-e-infusiones")
        .build();
  }

  @Test
  void searchOnlyReturnsProductsOnSale() {
    Page<ProductCard> page =
        queries.ask(new SearchProductsQuery(inCoffee(), PageRequest.of(0, 50, Sort.by("sku"))));

    assertThat(page.getContent())
        .extracting(ProductCard::sku)
        .containsExactly(
            "CAF-001", "CAF-002", "CAF-003", "CAF-004", "CAF-005", "CAF-006", "ELE-005", "LIB-004");
    assertThat(page.getContent()).allMatch(p -> p.status() == ProductStatus.ACTIVE);
  }

  private static QueryPlan<Product> taggedEcoOrTea() {
    return SpecificationQueryBuilder.forEntity(Product.class)
        .where("tags", Operators.IN, List.of("ecologico", "te"))
        .build();
  }

  @Test
  void productsMatchingSeveralValuesOfAToManyFilterAppearOnce() {
    Page<ProductCard> page =
        queries.ask(
            new SearchProductsQuery(taggedEcoOrTea(), PageRequest.of(0, 50, Sort.by("sku"))));

    assertThat(page.getContent()).extracting(ProductCard::sku).doesNotHaveDuplicates();
    assertThat(page.getTotalElements()).isEqualTo(8);
  }

  @Test
  void facetsCountEachProductOnceUnderToManyFilters() {
    CatalogFacets facets = queries.ask(new GetCatalogFacetsQuery(taggedEcoOrTea()));

    assertThat(facets.categories())
        .filteredOn(f -> f.value().equals("cafe-e-infusiones"))
        .singleElement()
        .extracting(FacetValue::count)
        .isEqualTo(4L);
  }

  @Test
  void facetsOfAFieldIgnoreTheFilterOnThatSameField() {
    QueryPlan<Product> fromAna =
        SpecificationQueryBuilder.forEntity(Product.class)
            .where("seller.id", Operators.IN, List.of("seller-ana"))
            .build();

    CatalogFacets facets = queries.ask(new GetCatalogFacetsQuery(fromAna));

    // Other sellers stay selectable, with the counts they would add.
    assertThat(facets.sellers())
        .extracting(FacetValue::value)
        .contains("seller-ana", "seller-carmen");
    // Other facets do follow the seller filter.
    assertThat(facets.categories())
        .extracting(FacetValue::value)
        .containsOnly("cafe-e-infusiones", "dulces");
  }

  @Test
  void clientFiltersCannotRevealDrafts() {
    QueryPlan<Product> drafts =
        SpecificationQueryBuilder.forEntity(Product.class)
            .where("status", Operators.EQUALS, ProductStatus.DRAFT)
            .build();

    Page<ProductCard> page = queries.ask(new SearchProductsQuery(drafts, PageRequest.of(0, 10)));

    assertThat(page.getTotalElements()).isZero();
  }

  @Test
  void searchPagesAndCountsTotals() {
    Page<ProductCard> page =
        queries.ask(new SearchProductsQuery(inCoffee(), PageRequest.of(1, 3, Sort.by("sku"))));

    assertThat(page.getTotalElements()).isEqualTo(8);
    assertThat(page.getTotalPages()).isEqualTo(3);
    assertThat(page.getContent())
        .extracting(ProductCard::sku)
        .containsExactly("CAF-004", "CAF-005", "CAF-006");
  }

  @Test
  void cardsCarryPublicSellerDataOnly() {
    Page<ProductCard> page =
        queries.ask(new SearchProductsQuery(inCoffee(), PageRequest.of(0, 1, Sort.by("sku"))));

    ProductCard card = page.getContent().getFirst();
    assertThat(card.seller().displayName()).isEqualTo("Tostadores Ana");
    assertThat(card.categories()).containsExactly("cafe-e-infusiones");
    assertThat(card.tags()).containsExactly("comercio-justo", "ecologico");
  }

  @Test
  void productPageBySlug() {
    ProductDetail detail =
        queries.ask(new GetProductQuery("cafe-de-colombia-en-grano-1-kg-caf-001"));

    assertThat(detail.sku()).isEqualTo("CAF-001");
    assertThat(detail.description()).contains("caramelo");
  }

  /** The product page of a seeded product after telling the inventory its stock. */
  private ProductDetail pageWithStock(String slug, Integer units) {
    ProductDetail detail = queries.ask(new GetProductQuery(slug));
    if (units == null) {
      inventory.silentAbout(detail.id());
    } else {
      inventory.stock(detail.id(), units);
    }
    return queries.ask(new GetProductQuery(slug));
  }

  @Test
  void productPageShowsTheStockTheInventoryReports() {
    assertThat(pageWithStock("cafe-de-colombia-en-grano-1-kg-caf-001", 12).availability())
        .isEqualTo(new Availability(StockStatus.IN_STOCK, 12));
  }

  @Test
  void fewUnitsLeftIsLowStock() {
    assertThat(pageWithStock("cafe-molido-natural-500-g-caf-002", 3).availability())
        .isEqualTo(new Availability(StockStatus.LOW_STOCK, 3));
  }

  @Test
  void noUnitsLeftIsOutOfStock() {
    assertThat(pageWithStock("cafetera-italiana-de-aluminio-6-tazas-caf-003", 0).availability())
        .isEqualTo(new Availability(StockStatus.OUT_OF_STOCK, 0));
  }

  @Test
  void aProductTheInventoryDoesNotKnowYetIsOutOfStock() {
    ProductDetail detail = queries.ask(new GetProductQuery("cafe-descafeinado-de-etiopia-caf-004"));

    assertThat(detail.availability()).isEqualTo(new Availability(StockStatus.OUT_OF_STOCK, 0));
  }

  @Test
  void theProductPageIsShownWhenTheInventoryDoesNotAnswer() {
    ProductDetail detail = pageWithStock("te-verde-matcha-ceremonial-30-g-caf-005", null);

    assertThat(detail.sku()).isEqualTo("CAF-005");
    assertThat(detail.availability()).isEqualTo(Availability.unknown());
  }

  @Test
  void productsNoLongerOnSaleHaveNoStock() {
    ProductDetail detail = queries.ask(new GetProductQuery("rooibos-con-vainilla-caf-008"));

    assertThat(detail.status()).isEqualTo(ProductStatus.DISCONTINUED);
    assertThat(detail.availability()).isNull();
  }

  @Test
  void draftsHaveNoProductPage() {
    assertThatThrownBy(
            () -> queries.ask(new GetProductQuery("cafe-de-guatemala-huehuetenango-caf-007")))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void facetsCountTheSameProductsTheSearchReturns() {
    CatalogFacets facets = queries.ask(new GetCatalogFacetsQuery(inCoffee()));

    assertThat(facets.categories())
        .filteredOn(f -> f.value().equals("cafe-e-infusiones"))
        .singleElement()
        .extracting(FacetValue::count)
        .isEqualTo(8L);
    assertThat(facets.sellers())
        .extracting(FacetValue::value)
        .contains("seller-ana", "seller-carmen", "seller-diego", "seller-elena");
    assertThat(facets.price().min()).isEqualByComparingTo("3.20");
    assertThat(facets.price().max()).isEqualByComparingTo("349.00");
  }

  @Test
  void facetTagsAreCounted() {
    CatalogFacets facets = queries.ask(new GetCatalogFacetsQuery(inCoffee()));

    assertThat(facets.tags())
        .filteredOn(f -> f.value().equals("ecologico"))
        .singleElement()
        .extracting(FacetValue::count)
        .isEqualTo(3L);
  }

  @Test
  void priceRangeCoversTheWholeCatalogWithoutFilters() {
    CatalogFacets facets = queries.ask(new GetCatalogFacetsQuery(all()));

    assertThat(facets.price().min()).isEqualByComparingTo("2.95");
    assertThat(facets.price().max()).isEqualByComparingTo("899.00");
  }

  @Test
  void categoriesListCountsProductsOnSale() {
    List<CategoryView> categories = queries.ask(new ListCategoriesQuery());

    assertThat(categories).hasSize(12);
    assertThat(categories)
        .filteredOn(c -> c.slug().equals("cafe-e-infusiones"))
        .singleElement()
        .extracting(CategoryView::activeProducts)
        .isEqualTo(8L);
  }
}
