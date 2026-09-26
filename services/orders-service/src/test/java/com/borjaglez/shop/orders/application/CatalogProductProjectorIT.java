package com.borjaglez.shop.orders.application;

import static com.borjaglez.shop.orders.OrdersTestSupport.at;
import static com.borjaglez.shop.orders.OrdersTestSupport.discontinued;
import static com.borjaglez.shop.orders.OrdersTestSupport.priceChanged;
import static com.borjaglez.shop.orders.OrdersTestSupport.published;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.shop.contracts.catalog.ProductPriceChanged;
import com.borjaglez.shop.eskit.ProcessedMessageRepository;
import com.borjaglez.shop.orders.application.projection.CatalogProductProjector;
import com.borjaglez.shop.orders.domain.CatalogProduct;
import com.borjaglez.shop.orders.domain.CatalogProductRepository;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;
import com.borjaglez.specrepository.core.Operators;

/**
 * The local copy of the catalog: what can be ordered and at which price. Catalog events are keyed
 * by message type, so ordering holds only within one event type and a price change may arrive
 * before the publication it follows.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "shop.checkout.enabled=false")
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class
})
class CatalogProductProjectorIT {

  private static final Instant T1 = Instant.parse("2026-09-01T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-09-02T10:00:00Z");

  @Autowired CatalogProductProjector projector;
  @Autowired CatalogProductRepository products;
  @Autowired ProcessedMessageRepository processed;

  private CatalogProduct product(UUID id) {
    return products.query().where("productId", Operators.EQUALS, id).findOne().orElseThrow();
  }

  @Test
  void aPublishedProductCanBeOrderedAtItsPrice() {
    UUID id = UUID.randomUUID();

    projector.on(published(id, "CAF-100", "5.00"));

    CatalogProduct product = product(id);
    assertThat(product.isAvailable()).isTrue();
    assertThat(product.getPrice()).isEqualByComparingTo("5.00");
    assertThat(product.getSku()).isEqualTo("CAF-100");
  }

  @Test
  void aRedeliveredEventIsAppliedOnce() {
    UUID id = UUID.randomUUID();
    ProductPriceChanged change = at(priceChanged(id, "5.00", "6.00"), T2);
    projector.on(at(published(id, "CAF-101", "5.00"), T1));

    projector.on(change);
    projector.on(change);

    assertThat(processed.query().where("messageId", Operators.EQUALS, change.getEventId()).count())
        .isEqualTo(1);
    assertThat(product(id).getPrice()).isEqualByComparingTo("6.00");
  }

  @Test
  void aLatePublicationDoesNotUndoANewerPrice() {
    UUID id = UUID.randomUUID();

    projector.on(at(priceChanged(id, "5.00", "7.00"), T2));
    projector.on(at(published(id, "CAF-102", "5.00"), T1));

    CatalogProduct product = product(id);
    assertThat(product.getPrice()).isEqualByComparingTo("7.00");
    assertThat(product.getSku()).isEqualTo("CAF-102");
    assertThat(product.isAvailable()).isTrue();
  }

  @Test
  void aLatePublicationDoesNotBringBackADiscontinuedProduct() {
    UUID id = UUID.randomUUID();

    projector.on(at(discontinued(id), T2));
    projector.on(at(published(id, "CAF-103", "5.00"), T1));

    assertThat(product(id).isAvailable()).isFalse();
  }

  @Test
  void aPriceChangeForAnUnknownProductDoesNotMakeItOrderable() {
    UUID id = UUID.randomUUID();

    projector.on(at(priceChanged(id, "5.00", "7.00"), T2));

    assertThat(product(id).isAvailable()).isFalse();
  }
}
