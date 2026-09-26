package com.borjaglez.shop.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.kafka.config.KafkaCqrsProperties;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaPartitionKeyStrategy;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.borjaglez.shop.contracts.orders.OrderConfirmed;
import com.borjaglez.shop.contracts.orders.OrderLine;
import com.borjaglez.shop.contracts.orders.OrderPlaced;
import com.borjaglez.shop.contracts.orders.OrderRejected;
import com.borjaglez.shop.notifications.TestContainers;
import com.borjaglez.shop.notifications.domain.Notification;
import com.borjaglez.shop.notifications.domain.NotificationKind;
import com.borjaglez.shop.notifications.domain.NotificationRepository;
import com.borjaglez.specrepository.core.Operators;

/**
 * Events as the Boot 4 services publish them, read by this Boot 3 service.
 *
 * <p>The payloads below are exactly what Jackson 3 wrote for real orders (taken from the orders
 * event store): nanosecond ISO instants, numbers for amounts, records for lines. They travel with
 * the headers the cqrs Kafka publisher adds, so the only difference from production is who wrote
 * the bytes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestContainers.class)
class CrossGenerationIT {

  private static final Duration PATIENCE = Duration.ofSeconds(30);

  @Autowired KafkaTemplate<String, byte[]> kafkaTemplate;
  @Autowired KafkaPartitionKeyStrategy partitionKeys;
  @Autowired MessageNamingStrategy naming;
  @Autowired KafkaTopicNamingStrategy topics;
  @Autowired KafkaCqrsProperties properties;
  @Autowired NotificationRepository notifications;

  /** Publishes {@code json} as the body of {@code event}, with the headers cqrs gives it. */
  private void publishAsBoot4(Event event, String json) {
    MessageSerializer jackson3Bytes =
        new MessageSerializer() {
          @Override
          public byte[] serialize(Object message) {
            return json.getBytes(StandardCharsets.UTF_8);
          }

          @Override
          public <T> T deserialize(byte[] data, Class<T> type) {
            throw new UnsupportedOperationException();
          }
        };
    new KafkaMessagePublisher(kafkaTemplate, jackson3Bytes, partitionKeys, naming)
        .publish(topics.topic(properties.getEvents().getTopic()), event);
  }

  private Optional<Notification> notice(String eventId) {
    return notifications.query().where("eventId", Operators.EQUALS, eventId).findOne();
  }

  private static OrderPlaced placedShape() {
    return new OrderPlaced(
        UUID.randomUUID(),
        "x",
        List.of(new OrderLine(UUID.randomUUID(), "x", "x", 1, BigDecimal.ONE)),
        BigDecimal.ONE,
        "EUR");
  }

  @Test
  void jackson3EventsAreReadWithAllTheirFields() {
    String orderId = UUID.randomUUID().toString();
    String confirmedId = UUID.randomUUID().toString();

    publishAsBoot4(
        placedShape(),
        """
        {"lines": [{"sku": "DES-005", "name": "Lentejas pardinas ecológicas", "quantity": 3,
          "productId": "55b14835-b47b-499e-a555-8b46d4205eb2", "unitPrice": 2.95}],
         "total": 8.85, "eventId": "%s", "orderId": "%s", "currency": "EUR",
         "customerId": "cliente-mateo", "occurredOn": "2026-09-25T22:23:33.382253759Z"}
        """
            .formatted(UUID.randomUUID(), orderId));
    publishAsBoot4(
        new OrderConfirmed(UUID.randomUUID(), UUID.randomUUID()),
        """
        {"eventId": "%s", "orderId": "%s", "paymentId": "53ea3fd7-5f26-47dd-9596-42d69e3bdce9",
         "occurredOn": "2026-09-25T22:23:34.593417296Z"}
        """
            .formatted(confirmedId, orderId));

    await()
        .atMost(PATIENCE)
        .untilAsserted(
            () ->
                assertThat(notice(confirmedId))
                    .hasValueSatisfying(
                        n -> {
                          // The id survived Jackson 3 → Jackson 2 on the Boot 3 side.
                          assertThat(n.getEventId()).isEqualTo(confirmedId);
                          assertThat(n.getOrderId()).hasToString(orderId);
                          assertThat(n.getCustomerId()).isEqualTo("cliente-mateo");
                          assertThat(n.getBody()).contains("€8.85");
                          // PostgreSQL keeps microseconds of the nanosecond instant Jackson 3
                          // wrote.
                          assertThat(n.getOccurredAt().getNano()).isEqualTo(593417000);
                        }));
  }

  @Test
  void aNoticeArrivingBeforeItsOrderWaitsForIt() {
    String orderId = UUID.randomUUID().toString();
    String rejectedId = UUID.randomUUID().toString();

    publishAsBoot4(
        new OrderRejected(UUID.randomUUID(), "x", "x"),
        """
        {"eventId": "%s", "orderId": "%s", "reason": "card-limit-exceeded",
         "detail": "The payment was declined", "occurredOn": "2026-09-26T10:00:00Z"}
        """
            .formatted(rejectedId, orderId));
    await()
        .atMost(PATIENCE)
        .untilAsserted(
            () ->
                assertThat(notice(rejectedId))
                    .hasValueSatisfying(n -> assertThat(n.isAddressed()).isFalse()));

    publishAsBoot4(
        placedShape(),
        """
        {"lines": [], "total": 950.00, "eventId": "%s", "orderId": "%s", "currency": "EUR",
         "customerId": "cliente-lucia", "occurredOn": "2026-09-26T09:59:59Z"}
        """
            .formatted(UUID.randomUUID(), orderId));

    await()
        .atMost(PATIENCE)
        .untilAsserted(
            () ->
                assertThat(notice(rejectedId))
                    .hasValueSatisfying(
                        n -> {
                          assertThat(n.getCustomerId()).isEqualTo("cliente-lucia");
                          assertThat(n.getKind()).isEqualTo(NotificationKind.ORDER_REJECTED);
                          assertThat(n.getBody()).contains("exceeds your card limit");
                        }));
  }

  @Test
  void aRedeliveredEventAddsNothing() {
    String orderId = UUID.randomUUID().toString();
    String confirmedId = UUID.randomUUID().toString();
    String json =
        """
        {"eventId": "%s", "orderId": "%s", "paymentId": "53ea3fd7-5f26-47dd-9596-42d69e3bdce9",
         "occurredOn": "2026-09-26T10:00:00Z"}
        """
            .formatted(confirmedId, orderId);

    publishAsBoot4(new OrderConfirmed(UUID.randomUUID(), UUID.randomUUID()), json);
    publishAsBoot4(new OrderConfirmed(UUID.randomUUID(), UUID.randomUUID()), json);

    await().atMost(PATIENCE).until(() -> notice(confirmedId).isPresent());
    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                notifications
                        .query()
                        .where("orderId", Operators.EQUALS, UUID.fromString(orderId))
                        .count()
                    == 1);
  }
}
