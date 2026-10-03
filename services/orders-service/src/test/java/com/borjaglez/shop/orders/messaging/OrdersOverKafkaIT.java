package com.borjaglez.shop.orders.messaging;

import static com.borjaglez.shop.orders.OrdersTestSupport.published;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.kafka.KafkaEventBus;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.eskit.EventStore;
import com.borjaglez.shop.eskit.RecordedEvent;
import com.borjaglez.shop.orders.application.command.CancelOrderCommand;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand.Item;
import com.borjaglez.shop.orders.application.query.GetOrderQuery;
import com.borjaglez.shop.orders.application.query.OrderViews.OrderDetail;
import com.borjaglez.shop.orders.checkout.FakeCheckout;
import com.borjaglez.shop.orders.domain.CatalogProduct;
import com.borjaglez.shop.orders.domain.CatalogProductRepository;
import com.borjaglez.shop.orders.domain.Order;
import com.borjaglez.shop.orders.domain.OrderStatus;
import com.borjaglez.shop.orders.domain.OrderView;
import com.borjaglez.shop.orders.domain.OrderViewRepository;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;
import com.borjaglez.specrepository.core.Operators;

/**
 * The whole flow through a real Kafka: a catalog event makes a product orderable, the order is
 * stored as events, the relay publishes them and the read model catches up.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "shop.checkout.poll-interval=100ms")
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  FakeCheckout.class
})
class OrdersOverKafkaIT {

  private static final Duration PATIENCE = Duration.ofSeconds(30);

  @Autowired KafkaEventBus kafka;
  @Autowired CommandBus commands;
  @Autowired QueryBus queries;
  @Autowired CatalogProductRepository catalog;
  @Autowired OrderViewRepository views;
  @Autowired EventStore eventStore;
  @Autowired JdbcTemplate jdbc;

  private boolean orderable(UUID productId) {
    return catalog
        .query()
        .where("productId", Operators.EQUALS, productId)
        .findOne()
        .map(CatalogProduct::isOrderable)
        .orElse(false);
  }

  private OrderStatus viewStatus(UUID orderId) {
    return views
        .query()
        .where("orderId", Operators.EQUALS, orderId)
        .findOne()
        .map(OrderView::getStatus)
        .orElse(null);
  }

  @Test
  void anOrderTravelsFromTheCatalogEventToTheReadModel() {
    UUID productId = UUID.randomUUID();
    kafka.publish(published(productId, "KAF-" + productId.toString().substring(0, 4), "7.25"));
    await().atMost(PATIENCE).until(() -> orderable(productId));

    UUID orderId =
        commands.dispatchAndReceive(
            new PlaceOrderCommand("cliente-lucia", List.of(new Item(productId, 2))));

    // The scheduled saga runner checks the order out (against fake inventory and payments).
    await().atMost(PATIENCE).until(() -> viewStatus(orderId) == OrderStatus.CONFIRMED);
    OrderDetail detail = queries.ask(new GetOrderQuery(orderId, "cliente-lucia"));
    assertThat(detail.total()).isEqualByComparingTo("14.50");

    commands.dispatchAndWait(new CancelOrderCommand(orderId, "cliente-lucia", "Me equivoqué"));

    await().atMost(PATIENCE).until(() -> viewStatus(orderId) == OrderStatus.CANCELLED);
    await()
        .atMost(PATIENCE)
        .untilAsserted(
            () ->
                assertThat(eventStore.load(Order.STREAM_TYPE, orderId.toString()))
                    .hasSize(3)
                    .extracting(RecordedEvent::publishedAt)
                    .doesNotContainNull());
  }

  @Test
  void eventsKeepTheirIdAcrossKafka() {
    UUID productId = UUID.randomUUID();
    kafka.publish(published(productId, "KID-" + productId.toString().substring(0, 4), "1.00"));
    await().atMost(PATIENCE).until(() -> orderable(productId));
    UUID orderId =
        commands.dispatchAndReceive(
            new PlaceOrderCommand("cliente-lucia", List.of(new Item(productId, 1))));
    String storedId =
        eventStore.load(Order.STREAM_TYPE, orderId.toString()).getFirst().event().getEventId();

    // The read model marks the event as applied under the id it received.
    await()
        .atMost(PATIENCE)
        .until(
            () ->
                jdbc.queryForObject(
                        "select count(*) from cqrs_processed_message"
                            + " where handler_id = 'orders.order-view' and message_id = ?",
                        Integer.class,
                        storedId)
                    == 1);
  }
}
