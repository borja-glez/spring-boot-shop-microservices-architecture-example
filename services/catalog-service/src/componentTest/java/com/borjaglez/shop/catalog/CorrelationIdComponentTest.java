package com.borjaglez.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.borjaglez.shop.contracts.catalog.ProductPublished;

/**
 * The correlation id of a request goes wherever the request does: back in the response, into the
 * events it causes, through the outbox to Kafka, and into the questions it asks over RabbitMQ.
 */
@CatalogComponentTest
class CorrelationIdComponentTest {

  private static final String HEADER = "X-Correlation-Id";

  @LocalServerPort int port;
  @Autowired EventsTopic kafka;
  @Autowired InventoryStandIn inventory;

  CatalogClient catalog;

  @BeforeEach
  void client() {
    catalog = new CatalogClient(port);
  }

  private static String newCorrelationId() {
    return "component-" + UUID.randomUUID();
  }

  private ResponseEntity<Void> publish(UUID id, String correlationId) {
    var request =
        catalog
            .http()
            .post()
            .uri(CatalogClient.PRODUCTS + "/{id}/publish", id)
            .header("X-Shop-User", CatalogClient.SELLER);
    if (correlationId != null) {
      request.header(HEADER, correlationId);
    }
    ResponseEntity<Void> published = request.retrieve().toBodilessEntity();
    assertThat(published.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    return published;
  }

  private String correlationIdOnKafka(UUID product) {
    return kafka
        .awaitReceived(ProductPublished.class, event -> event.getProductId().equals(product))
        .getFirst()
        .correlationId();
  }

  @Test
  void theEventOfAWriteReachesKafkaWithTheCorrelationIdOfTheRequest() {
    UUID id = catalog.create(CatalogClient.uniqueSku(), "Pazos de Galicia", "28.00");
    String correlationId = newCorrelationId();

    ResponseEntity<Void> published = publish(id, correlationId);

    assertThat(published.getHeaders().getFirst(HEADER)).isEqualTo(correlationId);
    assertThat(correlationIdOnKafka(id)).isEqualTo(correlationId);
  }

  @Test
  void aWriteWithoutACorrelationIdIsGivenOneThatItsEventCarries() {
    UUID id = catalog.create(CatalogClient.uniqueSku(), "Hórreos y cruceiros", "14.50");

    String given = publish(id, null).getHeaders().getFirst(HEADER);

    assertThat(given).isNotBlank();
    assertThat(correlationIdOnKafka(id)).isEqualTo(given);
  }

  @Test
  void theQuestionOfAProductPageReachesTheInventoryWithTheCorrelationIdOfTheRequest() {
    String slug = catalog.slugOf("CAF-004");
    UUID product = UUID.fromString((String) catalog.pageOf(slug).get("id"));
    String correlationId = newCorrelationId();

    ResponseEntity<Map<String, Object>> page =
        catalog
            .http()
            .get()
            .uri(CatalogClient.PRODUCTS + "/{slug}", slug)
            .header(HEADER, correlationId)
            .retrieve()
            .toEntity(new ParameterizedTypeReference<>() {});

    assertThat(page.getHeaders().getFirst(HEADER)).isEqualTo(correlationId);
    assertThat(inventory.questionsAbout(product))
        .extracting(InventoryStandIn.Question::correlationId)
        .contains(correlationId);
  }
}
