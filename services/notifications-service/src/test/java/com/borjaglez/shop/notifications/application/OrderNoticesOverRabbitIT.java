package com.borjaglez.shop.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.config.RabbitMqCqrsProperties;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.shop.contracts.notifications.GetOrderNotices;
import com.borjaglez.shop.contracts.orders.OrderPlaced;
import com.borjaglez.shop.notifications.TestContainers;
import com.borjaglez.shop.notifications.domain.Notification;
import com.borjaglez.shop.notifications.domain.NotificationKind;
import com.borjaglez.shop.notifications.domain.NotificationRepository;

/**
 * {@link GetOrderNotices} as the Boot 4 orders service sends it: the request body below is what
 * Jackson 3 writes for it, and the reply is read back as raw JSON, the bytes Jackson 3 will parse
 * on the other side.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "shop.notifications.sweep-interval=1h")
@Import(TestContainers.class)
class OrderNoticesOverRabbitIT {

  @Autowired RabbitTemplate rabbit;
  @Autowired RabbitMqNamingStrategy rabbitNaming;
  @Autowired MessageNamingStrategy naming;
  @Autowired RabbitMqCqrsProperties properties;
  @Autowired NotificationProjector projector;
  @Autowired NotificationRepository notifications;

  /** Sends the query with the headers the cqrs publisher adds and returns the reply body. */
  private String askAsBoot4(UUID orderId, String customer) {
    MessageProperties headers = new MessageProperties();
    headers.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    headers.setHeader("__TypeId__", GetOrderNotices.class.getName());
    headers.setHeader("cqrs.message.type", "query");
    String json =
        """
        {"queryId": "%s", "orderId": "%s", "customerId": "%s"}
        """
            .formatted(UUID.randomUUID(), orderId, customer);
    Message reply =
        rabbit.sendAndReceive(
            rabbitNaming.exchange(properties.getQueries().getExchange()),
            naming.queryName(GetOrderNotices.class),
            new Message(json.getBytes(StandardCharsets.UTF_8), headers));
    assertThat(reply).as("reply").isNotNull();
    return new String(reply.getBody(), StandardCharsets.UTF_8);
  }

  private void confirmedAt(UUID orderId, String customer, String at) {
    notifications.save(
        Notification.of(
            UUID.randomUUID().toString(),
            orderId,
            NotificationKind.ORDER_CONFIRMED,
            null,
            null,
            null,
            OffsetDateTime.parse(at)));
    projector.on(new OrderPlaced(orderId, customer, List.of(), new BigDecimal("12.50"), "EUR"));
  }

  @Test
  void theNoticesOfAnOrderTravelBackAsJsonJackson3Reads() {
    UUID orderId = UUID.randomUUID();
    confirmedAt(orderId, "cliente-lucia", "2026-09-29T10:15:30.123456Z");

    String reply = askAsBoot4(orderId, "cliente-lucia");

    assertThat(reply)
        .contains("\"kind\":\"ORDER_CONFIRMED\"")
        .contains("\"title\":\"Order confirmed\"")
        .contains("€12.50")
        // An ISO-8601 instant, not a number: both Jackson generations read it the same way.
        .contains("\"sentAt\":\"2026-09-29T10:15:30.123456Z\"")
        .contains("\"read\":false");
  }

  @Test
  void anotherCustomerGetsNoNotices() {
    UUID orderId = UUID.randomUUID();
    confirmedAt(orderId, "cliente-lucia", "2026-09-29T10:00:00Z");

    assertThat(askAsBoot4(orderId, "cliente-intruso")).isEqualTo("{\"notices\":[]}");
  }
}
