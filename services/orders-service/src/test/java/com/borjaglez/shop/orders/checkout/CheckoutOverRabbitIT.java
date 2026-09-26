package com.borjaglez.shop.orders.checkout;

import static com.borjaglez.shop.orders.OrdersTestSupport.published;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.rabbitmq.RemoteHandlerException;
import com.borjaglez.cqrs.rabbitmq.RemoteReplyTimeoutException;
import com.borjaglez.shop.contracts.inventory.ReleaseStock;
import com.borjaglez.shop.contracts.inventory.ReservationLine;
import com.borjaglez.shop.contracts.inventory.ReserveStock;
import com.borjaglez.shop.contracts.inventory.StockRelease;
import com.borjaglez.shop.contracts.inventory.StockReservation;
import com.borjaglez.shop.contracts.payments.AuthorizePayment;
import com.borjaglez.shop.contracts.payments.PaymentAuthorization;
import com.borjaglez.shop.contracts.payments.PaymentRefund;
import com.borjaglez.shop.contracts.payments.RefundPayment;
import com.borjaglez.shop.eskit.AggregateStore;
import com.borjaglez.shop.orders.application.checkout.InventoryGateway;
import com.borjaglez.shop.orders.application.checkout.PaymentsGateway;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand.Item;
import com.borjaglez.shop.orders.application.projection.CatalogProductProjector;
import com.borjaglez.shop.orders.domain.Order;
import com.borjaglez.shop.orders.domain.OrderStatus;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;

/**
 * The saga's gateways over a real RabbitMQ. The inventory and payment handlers live in this test
 * context, so every command travels through the broker and back as it would between services.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"shop.checkout.enabled=false", "spring.rabbitmq.template.reply-timeout=2s"})
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  CheckoutOverRabbitIT.RemoteServices.class,
  CheckoutDriver.class
})
class CheckoutOverRabbitIT {

  /** Stand-ins for inventory-service and payments-service, reached over RabbitMQ. */
  @TestConfiguration(proxyBeanMethods = false)
  @CommandHandler
  static class RemoteServices {

    final Map<UUID, String> calls = new ConcurrentHashMap<>();

    @HandleCommand
    public StockReservation reserve(ReserveStock command) {
      if (command.getLines().getFirst().quantity() == 99) {
        throw new IllegalStateException("warehouse on fire");
      }
      if (command.getLines().getFirst().quantity() == 98) {
        sleep(3000);
      }
      calls.merge(command.getOrderId(), "reserve", (a, b) -> a + "," + b);
      return new StockReservation(true, List.of());
    }

    @HandleCommand
    public StockRelease release(ReleaseStock command) {
      calls.merge(command.getOrderId(), "release", (a, b) -> a + "," + b);
      return new StockRelease(true);
    }

    @HandleCommand
    public PaymentAuthorization authorize(AuthorizePayment command) {
      calls.merge(command.getOrderId(), "authorize", (a, b) -> a + "," + b);
      return new PaymentAuthorization(true, UUID.randomUUID(), null);
    }

    @HandleCommand
    public PaymentRefund refund(RefundPayment command) {
      calls.merge(command.getOrderId(), "refund", (a, b) -> a + "," + b);
      return new PaymentRefund(true);
    }

    private static void sleep(long millis) {
      try {
        Thread.sleep(millis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Autowired InventoryGateway inventory;
  @Autowired PaymentsGateway payments;
  @Autowired RemoteServices remote;
  @Autowired CommandBus commands;
  @Autowired CatalogProductProjector catalog;
  @Autowired AggregateStore<Order> orders;
  @Autowired CheckoutDriver driver;

  private static ReservationLine line(int quantity) {
    return new ReservationLine(UUID.randomUUID(), "RAB-1", quantity);
  }

  @Test
  void theGatewaysTravelOverRabbitMq() {
    UUID orderId = UUID.randomUUID();

    assertThat(inventory.reserve(orderId, List.of(line(1))).reserved()).isTrue();
    assertThat(payments.authorize(orderId, "cliente-lucia", BigDecimal.TEN, "EUR").authorized())
        .isTrue();
    assertThat(payments.refund(orderId).refunded()).isTrue();
    assertThat(inventory.release(orderId).released()).isTrue();

    assertThat(remote.calls.get(orderId)).isEqualTo("reserve,authorize,refund,release");
  }

  @Test
  void remoteFailuresAndTimeoutsReachTheSaga() {
    assertThatThrownBy(() -> inventory.reserve(UUID.randomUUID(), List.of(line(99))))
        .isInstanceOf(RemoteHandlerException.class)
        .hasMessageContaining("warehouse on fire");
    assertThatThrownBy(() -> inventory.reserve(UUID.randomUUID(), List.of(line(98))))
        .isInstanceOf(RemoteReplyTimeoutException.class);
  }

  @Test
  void aWholeCheckoutRunsOverRabbitMq() {
    UUID productId = UUID.randomUUID();
    catalog.on(published(productId, "RAB-" + productId.toString().substring(0, 4), "3.00"));
    UUID orderId =
        commands.dispatchAndReceive(
            new PlaceOrderCommand("cliente-lucia", List.of(new Item(productId, 1))));

    driver.finish(orderId);

    assertThat(orders.load(orderId.toString()).orElseThrow().status())
        .isEqualTo(OrderStatus.CONFIRMED);
    assertThat(remote.calls.get(orderId)).isEqualTo("reserve,authorize");
  }
}
