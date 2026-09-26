package com.borjaglez.shop.orders.checkout;

import static com.borjaglez.shop.orders.OrdersTestSupport.published;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.shop.eskit.AggregateStore;
import com.borjaglez.shop.eskit.EventStore;
import com.borjaglez.shop.eskit.RecordedEvent;
import com.borjaglez.shop.orders.application.command.CancelOrderCommand;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand.Item;
import com.borjaglez.shop.orders.application.projection.CatalogProductProjector;
import com.borjaglez.shop.orders.domain.CheckoutSaga;
import com.borjaglez.shop.orders.domain.CheckoutStep;
import com.borjaglez.shop.orders.domain.Order;
import com.borjaglez.shop.orders.domain.OrderStatus;
import com.borjaglez.shop.orders.domain.SagaStepLog;
import com.borjaglez.shop.orders.domain.StepOutcome;
import com.borjaglez.shop.support.error.ConflictException;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;

/**
 * The checkout saga over a real database, with inventory and payments replaced by fakes that answer
 * as each test tells them. The scheduled runner is off: the tests drive the saga.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "shop.checkout.enabled=false",
      "shop.checkout.max-attempts=3",
      "shop.checkout.compensation-max-attempts=3",
      "shop.checkout.initial-backoff=0s",
      "shop.checkout.max-backoff=0s"
    })
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  FakeCheckout.class,
  CheckoutDriver.class
})
class CheckoutSagaIT {

  @Autowired CommandBus commands;
  @Autowired CatalogProductProjector catalog;
  @Autowired AggregateStore<Order> orders;
  @Autowired EventStore eventStore;
  @Autowired FakeCheckout fakes;
  @Autowired CheckoutDriver driver;

  private UUID place() {
    UUID productId = UUID.randomUUID();
    catalog.on(published(productId, "SAGA-" + productId.toString().substring(0, 4), "12.00"));
    return commands.dispatchAndReceive(
        new PlaceOrderCommand("cliente-lucia", List.of(new Item(productId, 2))));
  }

  private Order order(UUID orderId) {
    return orders.load(orderId.toString()).orElseThrow();
  }

  private List<String> orderEvents(UUID orderId) {
    return eventStore.load(Order.STREAM_TYPE, orderId.toString()).stream()
        .map(RecordedEvent::eventType)
        .map(type -> type.substring(type.lastIndexOf('.') + 1))
        .toList();
  }

  @Test
  void aSuccessfulCheckoutConfirmsTheOrder() {
    UUID orderId = place();

    CheckoutSaga saga = driver.finish(orderId);

    assertThat(saga.getState()).isEqualTo(CheckoutSaga.State.COMPLETED);
    assertThat(saga.getPaymentId()).isNotNull();
    assertThat(order(orderId).status()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order(orderId).paymentId()).isEqualTo(saga.getPaymentId());
    assertThat(fakes.script(orderId).calls()).containsExactly("reserve", "authorize");
    assertThat(orderEvents(orderId)).containsExactly("order-placed", "order-confirmed");
  }

  @Test
  void aShortageRejectsTheOrderAfterReleasing() {
    UUID placed = place();
    fakes.script(placed).outOfStock();

    CheckoutSaga saga = driver.finish(placed);

    assertThat(order(placed).status()).isEqualTo(OrderStatus.REJECTED);
    assertThat(saga.getRejectionReason()).isEqualTo("out-of-stock");
    assertThat(saga.getRejectionDetail()).contains("2 requested, 0 available");
    assertThat(fakes.script(placed).calls()).containsExactly("reserve", "release");
    assertThat(orderEvents(placed)).containsExactly("order-placed", "order-rejected");
  }

  @Test
  void aDeclinedCardRejectsTheOrderAndReleasesTheStock() {
    UUID orderId = place();
    fakes.script(orderId).declines("card-limit-exceeded");

    CheckoutSaga saga = driver.finish(orderId);

    assertThat(order(orderId).status()).isEqualTo(OrderStatus.REJECTED);
    assertThat(saga.getRejectionReason()).isEqualTo("card-limit-exceeded");
    assertThat(fakes.script(orderId).calls()).containsExactly("reserve", "authorize", "release");
  }

  @Test
  void technicalFailuresAreRetried() {
    UUID orderId = place();
    fakes.script(orderId).inventoryFails(2).paymentFails(1);

    CheckoutSaga saga = driver.finish(orderId);

    assertThat(order(orderId).status()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(saga.getLog())
        .extracting(SagaStepLog::getStep, SagaStepLog::getOutcome)
        .containsExactly(
            tuple(CheckoutStep.RESERVE_STOCK, StepOutcome.RETRYING),
            tuple(CheckoutStep.RESERVE_STOCK, StepOutcome.RETRYING),
            tuple(CheckoutStep.RESERVE_STOCK, StepOutcome.SUCCEEDED),
            tuple(CheckoutStep.AUTHORIZE_PAYMENT, StepOutcome.RETRYING),
            tuple(CheckoutStep.AUTHORIZE_PAYMENT, StepOutcome.SUCCEEDED),
            tuple(CheckoutStep.CONFIRM_ORDER, StepOutcome.SUCCEEDED));
  }

  @Test
  void anUnreachablePaymentServiceRejectsTheOrderAndUndoesEverything() {
    UUID orderId = place();
    fakes.script(orderId).paymentFails(99);

    CheckoutSaga saga = driver.finish(orderId);

    assertThat(order(orderId).status()).isEqualTo(OrderStatus.REJECTED);
    assertThat(saga.getRejectionReason()).isEqualTo("payment-unavailable");
    assertThat(fakes.script(orderId).calls())
        .containsExactly("reserve", "authorize", "authorize", "authorize", "refund", "release");
  }

  @Test
  void aCompensationThatKeepsFailingIsRetriedUntilItWorks() {
    UUID orderId = place();
    fakes.script(orderId).outOfStock().releaseFails(5);

    CheckoutSaga saga = driver.finish(orderId);

    assertThat(saga.getState()).isEqualTo(CheckoutSaga.State.COMPLETED);
    assertThat(order(orderId).status()).isEqualTo(OrderStatus.REJECTED);
    assertThat(fakes.script(orderId).calls())
        .containsExactly(
            "reserve", "release", "release", "release", "release", "release", "release");
  }

  @Test
  void cancellingAConfirmedOrderRefundsAndReleases() {
    UUID orderId = place();
    driver.finish(orderId);

    commands.dispatchAndWait(new CancelOrderCommand(orderId, "cliente-lucia", "Me equivoqué"));
    commands.dispatchAndWait(new CancelOrderCommand(orderId, "cliente-lucia", "Otra vez"));
    CheckoutSaga saga = driver.finish(orderId);

    assertThat(order(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(saga.getMode()).isEqualTo(CheckoutSaga.Mode.CANCELLING);
    assertThat(saga.getState()).isEqualTo(CheckoutSaga.State.COMPLETED);
    assertThat(fakes.script(orderId).calls())
        .containsExactly("reserve", "authorize", "refund", "release");
  }

  @Test
  void anOrderCannotBeCancelledDuringItsCheckout() {
    UUID orderId = place();

    assertThatThrownBy(
            () ->
                commands.dispatchAndWait(new CancelOrderCommand(orderId, "cliente-lucia", "Prisa")))
        .isInstanceOf(ConflictException.class)
        .hasFieldOrPropertyWithValue("code", "checkout-in-progress");
  }
}
