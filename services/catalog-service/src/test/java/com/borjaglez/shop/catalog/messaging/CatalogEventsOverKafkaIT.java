package com.borjaglez.shop.catalog.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.shop.catalog.RecordedEvents;
import com.borjaglez.shop.catalog.application.command.CreateProductCommand;
import com.borjaglez.shop.catalog.application.command.PublishProductCommand;
import com.borjaglez.shop.contracts.catalog.ProductPublished;
import com.borjaglez.shop.eskit.StoredEventRepository;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.specrepository.core.Operators;

/**
 * Catalog events leave through the outbox, travel through a real Kafka and come back to any
 * subscriber, here the catalog itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({PostgresTestConfiguration.class, KafkaTestConfiguration.class, RecordedEvents.class})
class CatalogEventsOverKafkaIT {

  private static final String PUBLISHED = "shop.catalog.1.event.product.product-published";

  @Autowired CommandBus commands;
  @Autowired RecordedEvents received;
  @Autowired StoredEventRepository outbox;

  @Test
  void aPublishedProductReachesKafkaSubscribers() {
    String sku = "KAF-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    UUID id =
        commands.dispatchAndReceive(
            new CreateProductCommand(
                "seller-ana",
                sku,
                "Café de Kenia",
                "Tueste claro",
                new BigDecimal("15.00"),
                "EUR",
                Set.of("cafe-e-infusiones"),
                Set.of()));

    commands.dispatchAndWait(new PublishProductCommand(id, "seller-ana"));

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(received.of(ProductPublished.class))
                    .anySatisfy(
                        event -> {
                          assertThat(event.getProductId()).isEqualTo(id);
                          assertThat(event.getSku()).isEqualTo(sku);
                        }));
  }

  @Test
  void seedProductsAreBackfilledIntoTheOutboxAndPublished() {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        outbox
                            .query()
                            .where("streamType", Operators.EQUALS, "product")
                            .where("eventType", Operators.EQUALS, PUBLISHED)
                            .where("publishedAt", Operators.IS_NOT_NULL, null)
                            .count())
                    .isGreaterThanOrEqualTo(62));
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(received.of(ProductPublished.class))
                    .anySatisfy(event -> assertThat(event.getSku()).isEqualTo("CAF-001")));
  }
}
