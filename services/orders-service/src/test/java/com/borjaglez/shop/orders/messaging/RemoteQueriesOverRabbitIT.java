package com.borjaglez.shop.orders.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.contracts.inventory.GetStockLevels;
import com.borjaglez.shop.contracts.inventory.StockLevel;
import com.borjaglez.shop.contracts.inventory.StockLevels;
import com.borjaglez.shop.contracts.notifications.GetOrderNotices;
import com.borjaglez.shop.contracts.notifications.OrderNotice;
import com.borjaglez.shop.contracts.notifications.OrderNotices;
import com.borjaglez.shop.orders.application.query.OrderNoticesGateway;
import com.borjaglez.shop.orders.application.query.StockLevelsGateway;
import com.borjaglez.shop.orders.checkout.FakeCheckout;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;

/**
 * The reads from inventory and notifications over a real RabbitMQ. Their handlers live in this test
 * context, so every query travels through the broker and back as it would between services, with
 * the short reply timeout of the remote queries, not the checkout's.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"shop.checkout.enabled=false", "shop.remote-queries.reply-timeout=500ms"})
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  FakeCheckout.class,
  RemoteQueriesOverRabbitIT.RemoteServices.class
})
class RemoteQueriesOverRabbitIT {

  static final UUID SLOW = UUID.randomUUID();
  static final UUID BROKEN = UUID.randomUUID();
  static final OffsetDateTime SENT = OffsetDateTime.parse("2026-09-29T10:15:30.123456789Z");

  /** Stand-ins for inventory-service and notifications-service, reached over RabbitMQ. */
  @TestConfiguration(proxyBeanMethods = false)
  @QueryHandler
  static class RemoteServices {

    @HandleQuery
    public StockLevels levels(GetStockLevels query) {
      if (query.getProductIds().contains(SLOW)) {
        sleep(1500);
      }
      return new StockLevels(
          query.getProductIds().stream().map(id -> new StockLevel(id, 3)).toList());
    }

    @HandleQuery
    public OrderNotices notices(GetOrderNotices query) {
      if (query.getOrderId().equals(BROKEN)) {
        throw new IllegalStateException("mail server on fire");
      }
      return new OrderNotices(
          List.of(
              new OrderNotice(
                  "ORDER_CONFIRMED", "Confirmed", "For " + query.getCustomerId(), SENT, true)));
    }

    private static void sleep(long millis) {
      try {
        Thread.sleep(millis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Autowired StockLevelsGateway stock;
  @Autowired OrderNoticesGateway notices;

  @Test
  void stockLevelsTravelOverRabbitMq() {
    UUID productId = UUID.randomUUID();

    assertThat(stock.available(List.of(productId)))
        .hasValueSatisfying(
            units -> assertThat(units).containsExactlyEntriesOf(Map.of(productId, 3)));
  }

  @Test
  void aSlowInventoryAnswersNothingWithinTheShortTimeout() {
    assertThat(stock.available(List.of(SLOW))).isEmpty();
  }

  @Test
  void noticesTravelOverRabbitMq() {
    assertThat(notices.notices(UUID.randomUUID(), "cliente-lucia"))
        .hasValueSatisfying(
            list ->
                assertThat(list)
                    .singleElement()
                    .satisfies(
                        n -> {
                          assertThat(n.body()).isEqualTo("For cliente-lucia");
                          assertThat(n.sentAt()).isEqualTo(SENT);
                        }));
  }

  @Test
  void aFailingNotificationsServiceAnswersNothing() {
    assertThat(notices.notices(BROKEN, "cliente-lucia")).isEmpty();
  }
}
