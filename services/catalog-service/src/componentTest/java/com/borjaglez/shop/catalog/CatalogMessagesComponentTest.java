package com.borjaglez.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import com.borjaglez.cqrs.introspection.CqrsIntrospection;
import com.borjaglez.shop.catalog.EventsTopic.Received;
import com.borjaglez.shop.catalog.infrastructure.in.messaging.CatalogEventsLogger;
import com.borjaglez.shop.contracts.catalog.ProductPublished;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * The messages of the catalog as the running application exchanges them, against {@code
 * catalog.yaml}: what it receives has a handler that reads it from Kafka, and the seed products are
 * announced there.
 */
@CatalogComponentTest
class CatalogMessagesComponentTest {

  /** The correlation id the seed publications carry (V1002). */
  private static final String SEED = "catalog-seed";

  /** The seed products on sale (V2__catalog_seed.sql). */
  private static final int SEEDED_ON_SALE = 62;

  @LocalServerPort int port;
  @Autowired CqrsIntrospection introspection;
  @Autowired CatalogMessageContract contract;
  @Autowired EventsTopic kafka;
  @Autowired JdbcTemplate jdbc;

  CatalogClient catalog;

  @BeforeEach
  void client() {
    catalog = new CatalogClient(port);
  }

  @Test
  void everyReceivedMessageHasAHandler() {
    contract.verifier().verifyHandlers(introspection);
  }

  /**
   * The catalog reads its own events back ({@code receiveCatalogEvents}): its cqrs consumer takes
   * them from Kafka and {@link CatalogEventsLogger} logs them.
   */
  @Test
  void aPublishedProductIsReadBackFromKafkaByTheCatalog() {
    Logger logger = (Logger) LoggerFactory.getLogger(CatalogEventsLogger.class);
    ListAppender<ILoggingEvent> logged = new ListAppender<>();
    logged.start();
    logger.addAppender(logged);
    try {
      String sku = CatalogClient.uniqueSku();

      catalog.onSale(sku, "Illas Cíes", "16.00");

      await()
          .atMost(Duration.ofSeconds(30))
          .until(
              () -> lines(logged),
              lines -> lines.contains("Product " + sku + " published at 16.00 EUR"));
    } finally {
      logger.detachAppender(logged);
    }
  }

  /**
   * What the appender holds, read under its lock: Kafka's consumer thread writes to it meanwhile.
   */
  private static List<String> lines(ListAppender<ILoggingEvent> appender) {
    synchronized (appender) {
      return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
  }

  /**
   * On a new database, each seed product on sale reaches Kafka once, under the correlation id of
   * the seed, with its data and a payload that follows the contract.
   */
  @Test
  void seedProductsAreAnnounced() {
    List<Received<ProductPublished>> announced =
        await()
            .atMost(Duration.ofSeconds(30))
            .pollInSameThread()
            .until(
                () ->
                    kafka.received(ProductPublished.class).stream()
                        .filter(received -> SEED.equals(received.correlationId()))
                        .toList(),
                found -> found.size() >= SEEDED_ON_SALE);

    Map<UUID, Map<String, Object>> seededOnSale =
        jdbc
            .queryForList(
                """
                select id, sku, name, price_amount, price_currency, seller_id from product
                where status = 'ACTIVE' and sku not like ?
                """,
                CatalogClient.SKU_PREFIX + "%")
            .stream()
            .collect(Collectors.toMap(row -> (UUID) row.get("id"), Function.identity()));
    assertThat(seededOnSale).hasSize(SEEDED_ON_SALE);
    assertThat(announced).hasSize(SEEDED_ON_SALE);
    Set<UUID> products =
        announced.stream()
            .map(received -> received.event().getProductId())
            .collect(Collectors.toSet());
    assertThat(products).isEqualTo(seededOnSale.keySet());
    for (Received<ProductPublished> received : announced) {
      ProductPublished event = received.event();
      Map<String, Object> product = seededOnSale.get(event.getProductId());
      assertThat(event.getSku()).isEqualTo(product.get("sku"));
      assertThat(event.getName()).isEqualTo(product.get("name"));
      assertThat(event.getPrice()).isEqualByComparingTo((BigDecimal) product.get("price_amount"));
      assertThat(event.getCurrency()).isEqualTo(product.get("price_currency"));
      assertThat(event.getSellerId()).isEqualTo(product.get("seller_id"));
      assertThat(received.key()).isEqualTo(received.name());
    }
    contract.assertPayloadsFollowIt(announced);
  }
}
