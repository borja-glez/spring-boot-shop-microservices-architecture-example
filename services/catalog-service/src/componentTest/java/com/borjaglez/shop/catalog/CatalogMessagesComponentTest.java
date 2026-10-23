package com.borjaglez.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.borjaglez.cqrs.asyncapi.AsyncApiVerifier;
import com.borjaglez.cqrs.introspection.CqrsIntrospection;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.borjaglez.shop.catalog.EventsTopic.Received;
import com.borjaglez.shop.contracts.catalog.ProductPublished;
import com.borjaglez.shop.testsupport.contract.AsyncApiContracts;

/**
 * The messages of the catalog as the running application exchanges them, against {@code
 * catalog.yaml}: what it receives has a handler, and the seed products are announced on Kafka.
 */
@CatalogComponentTest
class CatalogMessagesComponentTest {

  private static final Path CONTRACT = Path.of("src/main/asyncapi/catalog.yaml");

  /** The correlation id the seed publications carry (V1002). */
  private static final String SEED = "catalog-seed";

  /** The seed products on sale (V2__catalog_seed.sql). */
  private static final int SEEDED_ON_SALE = 62;

  @Autowired CqrsIntrospection introspection;
  @Autowired MessageSerializer serializer;
  @Autowired MessageNamingStrategy naming;
  @Autowired EventsTopic kafka;
  @Autowired JdbcTemplate jdbc;

  private AsyncApiVerifier verifier() {
    return AsyncApiContracts.verifier(CONTRACT, serializer, naming);
  }

  @Test
  void everyReceivedMessageHasAHandler() {
    verifier().verifyHandlers(introspection);
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
    AsyncApiVerifier contract = verifier();
    for (Received<ProductPublished> received : announced) {
      ProductPublished event = received.event();
      Map<String, Object> product = seededOnSale.get(event.getProductId());
      assertThat(event.getSku()).isEqualTo(product.get("sku"));
      assertThat(event.getName()).isEqualTo(product.get("name"));
      assertThat(event.getPrice()).isEqualByComparingTo((BigDecimal) product.get("price_amount"));
      assertThat(event.getCurrency()).isEqualTo(product.get("price_currency"));
      assertThat(event.getSellerId()).isEqualTo(product.get("seller_id"));
      assertThat(received.key()).isEqualTo(received.name());
      contract.sample(received.name(), received.json());
    }
    contract.verifyPayloads();
  }
}
