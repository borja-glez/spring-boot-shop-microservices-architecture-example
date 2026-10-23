package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.borjaglez.shop.testsupport.PostgresTestConfiguration;

/**
 * The catalog's migrations and es-kit's on a database of their own, as a new installation gets
 * them: the schema, its constraints and the demo seed (V2__catalog_seed.sql). Every test runs in a
 * transaction that is rolled back.
 */
@JdbcTest
@Import(PostgresTestConfiguration.class)
class CatalogMigrationsIT {

  /** A product row the schema accepts, with the given SKU, slug, price, status and seller. */
  private static final String PRODUCT =
      """
      insert into product (id, sku, slug, name, description, price_amount, price_currency,
                           status, seller_id, created_at, updated_at)
      values (gen_random_uuid(), '%s', '%s', 'Producto', '', %s, 'EUR', '%s', '%s', now(), now())
      """;

  @Autowired Flyway flyway;
  @Autowired JdbcTemplate jdbc;

  private long count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  @Test
  void everyMigrationIsAppliedInOrder() {
    MigrationInfo[] applied = flyway.info().applied();

    assertThat(applied)
        .extracting(migration -> migration.getVersion().getVersion())
        .containsExactly("1", "2", "1000", "1000.1", "1000.2", "1001", "1002");
    assertThat(applied).allMatch(migration -> migration.getState() == MigrationState.SUCCESS);
    assertThat(flyway.info().pending()).isEmpty();
  }

  @Test
  void createsTheTablesOfTheCatalogTheEventStoreAndTheOutbox() {
    List<String> tables =
        jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = current_schema()",
            String.class);

    assertThat(tables)
        .containsExactlyInAnyOrder(
            "flyway_schema_history",
            "seller",
            "category",
            "product",
            "product_category",
            "product_tag",
            "event_store",
            "cqrs_processed_message",
            "cqrs_outbox");
  }

  @Test
  void textSearchesCanIgnoreAccents() {
    assertThat(jdbc.queryForObject("select unaccent('Café de Colombia')", String.class))
        .isEqualTo("Cafe de Colombia");
  }

  @Test
  void aValidProductIsAccepted() {
    jdbc.update(PRODUCT.formatted("NEW-001", "producto-new-001", "1.00", "DRAFT", "seller-ana"));

    assertThat(count("select count(*) from product where sku = 'NEW-001'")).isOne();
  }

  static Stream<Arguments> rowsTheSchemaRejects() {
    return Stream.of(
        arguments(
            "a repeated SKU",
            PRODUCT.formatted("CAF-001", "producto-new-001", "1.00", "DRAFT", "seller-ana")),
        arguments(
            "a repeated slug",
            PRODUCT.formatted(
                "NEW-001",
                "cafe-de-colombia-en-grano-1-kg-caf-001",
                "1.00",
                "DRAFT",
                "seller-ana")),
        arguments(
            "a price of zero",
            PRODUCT.formatted("NEW-001", "producto-new-001", "0", "DRAFT", "seller-ana")),
        arguments(
            "an unknown status",
            PRODUCT.formatted("NEW-001", "producto-new-001", "1.00", "SOLD_OUT", "seller-ana")),
        arguments(
            "an unknown seller",
            PRODUCT.formatted("NEW-001", "producto-new-001", "1.00", "DRAFT", "seller-nadie")),
        arguments(
            "a product without a name",
            PRODUCT
                .formatted("NEW-001", "producto-new-001", "1.00", "DRAFT", "seller-ana")
                .replace("'Producto'", "null")),
        arguments(
            "an unknown category of a product",
            """
            insert into product_category (product_id, category_id)
            select id, gen_random_uuid() from product where sku = 'CAF-001'
            """),
        arguments(
            "a repeated tag of a product",
            """
            insert into product_tag (product_id, tag)
            select id, 'ecologico' from product where sku = 'CAF-001'
            """),
        arguments(
            "a repeated category slug",
            "insert into category (id, slug, name) values (gen_random_uuid(), 'vinos', 'Vinos')"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  void rowsTheSchemaRejects(String description, String sql) {
    assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void theSeedHasSixSellersAndTwelveCategories() {
    assertThat(count("select count(*) from seller")).isEqualTo(6);
    assertThat(count("select count(*) from category")).isEqualTo(12);
  }

  @Test
  void theSeedHasSeventyTwoProductsInEveryStatus() {
    List<Map<String, Object>> statuses =
        jdbc.queryForList("select status, count(*) as total from product group by status");

    assertThat(statuses)
        .containsExactlyInAnyOrder(
            Map.of("status", "ACTIVE", "total", 62L),
            Map.of("status", "DRAFT", "total", 5L),
            Map.of("status", "DISCONTINUED", "total", 5L));
  }

  @Test
  void onlyTheSeedDraftsAreUnpublished() {
    assertThat(
            jdbc.queryForList(
                "select sku from product where published_at is null order by sku", String.class))
        .containsExactly("CAF-007", "DES-007", "ELE-008", "EMB-005", "HOG-008");
    assertThat(count("select count(*) from product where status = 'DRAFT'")).isEqualTo(5);
  }

  @Test
  void everySeedProductHasACategoryAPriceInEurosAndASlugEndingInItsSku() {
    assertThat(
            count(
                "select count(*) from product p where not exists"
                    + " (select 1 from product_category c where c.product_id = p.id)"))
        .isZero();
    assertThat(count("select count(*) from product where price_currency <> 'EUR'")).isZero();
    assertThat(count("select count(*) from product where slug not like '%-' || lower(sku)"))
        .isZero();
    assertThat(jdbc.queryForObject("select slug from product where sku = 'ACE-004'", String.class))
        .isEqualTo("aceite-d-oliva-de-l-emporda-ace-004");
  }
}
