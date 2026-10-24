package com.borjaglez.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import com.borjaglez.shop.catalog.EventsTopic.Received;
import com.borjaglez.shop.contracts.catalog.ProductDiscontinued;
import com.borjaglez.shop.contracts.catalog.ProductPriceChanged;
import com.borjaglez.shop.contracts.catalog.ProductPublished;

/**
 * A seller's product from its creation to its withdrawal, over HTTP and through the real buses,
 * database and brokers: what each step stores, what the public catalog shows and what reaches the
 * other services on Kafka, checked against {@code catalog.yaml}. Every command runs in a
 * transaction of the command bus, which writes the product and the events of the change together.
 */
@CatalogComponentTest
class ProductLifecycleComponentTest {

  @LocalServerPort int port;
  @Autowired EventsTopic kafka;
  @Autowired JdbcTemplate jdbc;
  @Autowired CatalogMessageContract contract;

  CatalogClient catalog;

  @BeforeEach
  void client() {
    catalog = new CatalogClient(port);
  }

  /** The events the event store keeps for the product, by type. */
  private long storedEvents(UUID product, String type) {
    return jdbc.queryForObject(
        "select count(*) from event_store where stream_type = 'product' and stream_id = ?"
            + " and event_type = ?",
        Long.class,
        product.toString(),
        type);
  }

  private static BigDecimal amount(Object json) {
    return new BigDecimal(json.toString());
  }

  @Test
  void aDraftIsNeitherFoundNorAnnounced() {
    String sku = CatalogClient.uniqueSku();

    UUID id = catalog.create(sku, "Guía de la Ribeira Sacra", "19.00");

    assertThat(catalog.cardsOf(sku)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from event_store where stream_id = ?", Long.class, id.toString()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from cqrs_outbox where convert_from(payload, 'UTF8') like ?",
                Long.class,
                "%" + id + "%"))
        .isZero();
  }

  @Test
  void aPublishedProductIsFoundAndAnnouncedOnKafka() {
    String sku = CatalogClient.uniqueSku();
    UUID id = catalog.create(sku, "Atlas de Galicia", "31.00");

    assertThat(catalog.publish(id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(catalog.cardsOf(sku))
        .singleElement()
        .satisfies(
            card ->
                assertThat(card)
                    .containsEntry("id", id.toString())
                    .containsEntry("name", "Atlas de Galicia")
                    .containsEntry("status", "ACTIVE")
                    .containsEntry("categories", List.of("libros")));
    Received<ProductPublished> published =
        kafka
            .awaitReceived(ProductPublished.class, event -> event.getProductId().equals(id))
            .getFirst();
    assertThat(published.key()).isEqualTo(published.name());
    assertThat(published.event())
        .satisfies(
            event -> {
              assertThat(event.getSku()).isEqualTo(sku);
              assertThat(event.getName()).isEqualTo("Atlas de Galicia");
              assertThat(event.getPrice()).isEqualByComparingTo("31.00");
              assertThat(event.getCurrency()).isEqualTo("EUR");
              assertThat(event.getSellerId()).isEqualTo(CatalogClient.SELLER);
            });
    assertThat(storedEvents(id, "product-published.v1")).isOne();
    contract.assertPayloadsFollowIt(List.of(published));
  }

  @Test
  void aPriceChangeIsWrittenWithItsEventAndAnnounced() {
    String sku = CatalogClient.uniqueSku();
    UUID id = catalog.onSale(sku, "Camino de Santiago en bici", "24.00");
    String slug = catalog.slugOf(sku);

    assertThat(catalog.changePrice(id, "21.50", "EUR").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    Map<String, Object> page = catalog.pageOf(slug);
    assertThat(amount(page.get("price"))).isEqualByComparingTo("21.50");
    assertThat(page).containsEntry("version", 2);
    // PostgreSQL stamps each row version with the transaction that wrote it (xmin): the bus's
    // transaction wrote the new price and the event of the change.
    Map<String, Object> writers =
        jdbc.queryForMap(
            """
            select p.xmin::text as product, e.xmin::text as event
            from product p
            join event_store e on e.stream_type = 'product' and e.stream_id = p.id::text
            where p.id = ? and e.event_type = 'product-price-changed.v1'
            """,
            id);
    assertThat(writers.get("event")).isEqualTo(writers.get("product"));
    Received<ProductPriceChanged> changed =
        kafka
            .awaitReceived(ProductPriceChanged.class, event -> event.getProductId().equals(id))
            .getFirst();
    assertThat(changed.event().getOldPrice()).isEqualByComparingTo("24.00");
    assertThat(changed.event().getNewPrice()).isEqualByComparingTo("21.50");
    assertThat(changed.event().getCurrency()).isEqualTo("EUR");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from cqrs_outbox where event_id = ?",
                Long.class,
                changed.event().getEventId()))
        .as("relayed from the outbox")
        .isOne();
    contract.assertPayloadsFollowIt(List.of(changed));
  }

  @Test
  void aRejectedPriceChangeLeavesTheProductAsItWas() {
    String sku = CatalogClient.uniqueSku();
    UUID id = catalog.onSale(sku, "Faros de la Costa da Morte", "15.00");
    String slug = catalog.slugOf(sku);

    var rejected = catalog.changePrice(id, "17.00", "USD");

    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(rejected.getBody()).containsEntry("code", "currency-mismatch");
    Map<String, Object> page = catalog.pageOf(slug);
    assertThat(amount(page.get("price"))).isEqualByComparingTo("15.00");
    assertThat(page).containsEntry("version", 1);
    assertThat(storedEvents(id, "product-price-changed.v1")).isZero();
  }

  @Test
  void aWithdrawnProductLeavesTheSearchAndIsAnnouncedOnce() {
    String sku = CatalogClient.uniqueSku();
    UUID id = catalog.onSale(sku, "Mapa de los Ancares", "9.90");
    String slug = catalog.slugOf(sku);

    assertThat(catalog.discontinue(id, "Out of print").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(catalog.discontinue(id, "Out of print again").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(catalog.cardsOf(sku)).isEmpty();
    assertThat(catalog.pageOf(slug))
        .containsEntry("status", "DISCONTINUED")
        .containsEntry("availability", null);
    assertThat(storedEvents(id, "product-discontinued.v1")).isOne();
    Received<ProductDiscontinued> discontinued =
        kafka
            .awaitReceived(ProductDiscontinued.class, event -> event.getProductId().equals(id))
            .getFirst();
    assertThat(discontinued.event().getReason()).isEqualTo("Out of print");
    contract.assertPayloadsFollowIt(List.of(discontinued));
  }
}
