package com.borjaglez.shop.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;

import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.ProductRepository;
import com.borjaglez.shop.catalog.domain.ProductStatus;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.specrepository.boot4.SpecificationRepositoryAutoConfiguration;
import com.borjaglez.specrepository.core.GroupedRow;
import com.borjaglez.specrepository.core.Operators;

/**
 * Reads the seeded catalog (V2__catalog_seed.sql) through the specification-repository DSL on a
 * real PostgreSQL: enums, embedded records, many-to-many, element collections and grouping.
 */
@DataJpaTest
@Import(PostgresTestConfiguration.class)
@ImportAutoConfiguration(SpecificationRepositoryAutoConfiguration.class)
class ProductRepositoryIT {

  private static final int SEEDED = 72;
  private static final int SEEDED_ACTIVE = 62;

  @Autowired ProductRepository products;

  @Test
  void seedIsLoaded() {
    assertThat(products.count()).isEqualTo(SEEDED);
  }

  @Test
  void filtersByEnumStatus() {
    List<Product> active =
        products.query().where("status", Operators.EQUALS, ProductStatus.ACTIVE).findAll();

    assertThat(active).hasSize(SEEDED_ACTIVE);
    assertThat(active).allMatch(p -> p.getStatus() == ProductStatus.ACTIVE);
  }

  @Test
  void filtersByEnumStatusGivenAsString() {
    assertThat(products.query().where("status", Operators.EQUALS, "ACTIVE").count())
        .isEqualTo(SEEDED_ACTIVE);
  }

  @Test
  void traversesManyToManyCategories() {
    List<String> skus =
        products
            .query()
            .where("categories.slug", Operators.EQUALS, "cafe-e-infusiones")
            .sort(Sort.by("sku"))
            .findAll()
            .stream()
            .map(Product::getSku)
            .toList();

    assertThat(skus)
        .containsExactly(
            "CAF-001", "CAF-002", "CAF-003", "CAF-004", "CAF-005", "CAF-006", "CAF-007", "CAF-008",
            "ELE-005", "LIB-004");
  }

  @Test
  void filtersOnEmbeddedRecordField() {
    List<Product> affordable =
        products
            .query()
            .where(
                "price.amount",
                Operators.BETWEEN,
                List.of(new BigDecimal("5"), new BigDecimal("20")))
            .where("status", Operators.EQUALS, ProductStatus.ACTIVE)
            .findAll();

    assertThat(affordable).hasSize(33);
    assertThat(affordable)
        .allSatisfy(
            p ->
                assertThat(p.getPrice().amount())
                    .isBetween(new BigDecimal("5"), new BigDecimal("20")));
  }

  @Test
  void sortsByEmbeddedRecordField() {
    List<Product> cheapestFirst =
        products
            .query()
            .where("status", Operators.EQUALS, ProductStatus.ACTIVE)
            .sort(Sort.by("price.amount"))
            .findAll();

    assertThat(cheapestFirst.getFirst().getSku()).isEqualTo("DES-005");
    assertThat(cheapestFirst.getLast().getSku()).isEqualTo("ELE-003");
  }

  @Test
  void containsIsCaseInsensitiveWhenAsked() {
    long matches =
        products.query().where("name", Operators.CONTAINS, "CAFETERA", true, false).count();

    assertThat(matches).isEqualTo(2);
  }

  @Test
  void containsIgnoresAccentsOfTheSearchTermToo() {
    List<String> skus =
        products.query().where("name", Operators.CONTAINS, "café", true, false).findAll().stream()
            .map(Product::getSku)
            .toList();

    assertThat(skus).contains("CAF-001", "CAF-002", "CAF-003", "ELE-005");
  }

  @Test
  void filtersOnElementCollectionOfStrings() {
    List<String> skus =
        products.query().where("tags", Operators.EQUALS, "ecologico").findAll().stream()
            .map(Product::getSku)
            .sorted()
            .toList();

    assertThat(skus)
        .containsExactly(
            "CAF-001", "CAF-004", "CAF-005", "DES-005", "DES-007", "DUL-004", "DUL-006", "TEX-004");
  }

  @Test
  void groupsProductsByStatus() {
    List<GroupedRow> rows =
        products.query().groupBy("status").select("status").countAs("total", "id").findAllGrouped();

    Map<Object, Object> totals =
        rows.stream().collect(Collectors.toMap(r -> r.get("status"), r -> r.get("total")));
    assertThat(totals)
        .containsEntry(ProductStatus.ACTIVE, 62L)
        .containsEntry(ProductStatus.DRAFT, 5L)
        .containsEntry(ProductStatus.DISCONTINUED, 5L);
  }

  @Test
  void fetchesSellerTogetherWithProducts() {
    List<Product> page =
        products
            .query()
            .where("seller.id", Operators.EQUALS, "seller-diego")
            .leftFetch("seller")
            .findAll();

    assertThat(page).isNotEmpty();
    assertThat(page).allMatch(p -> "Diego Electrónica".equals(p.getSeller().getDisplayName()));
  }
}
