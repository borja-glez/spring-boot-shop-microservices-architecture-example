package com.borjaglez.shop.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.contracts.orders.OrderCancelled;
import com.borjaglez.shop.contracts.orders.OrderConfirmed;
import com.borjaglez.shop.contracts.orders.OrderPlaced;
import com.borjaglez.shop.contracts.orders.OrderRejected;
import com.borjaglez.shop.support.error.BusinessRuleViolationException;
import com.borjaglez.shop.support.error.ConflictException;

class OrderTest {

  private static final UUID ORDER = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
  private static final UUID COFFEE = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
  private static final UUID OIL = UUID.fromString("16fd2706-8baf-433b-82eb-8c7fada847da");
  private static final UUID PAYMENT = UUID.fromString("5b0f6c2e-1d3a-4c55-9e0b-7a1f2c3d4e5f");

  private static PricedLine coffee(int quantity) {
    return new PricedLine(
        COFFEE, "CAF-001", "Café de Colombia", quantity, new BigDecimal("18.90"), "EUR");
  }

  private static PricedLine oil(int quantity) {
    return new PricedLine(
        OIL, "ACE-002", "Aceite arbequina", quantity, new BigDecimal("9.90"), "EUR");
  }

  @Test
  void placingRecordsTheOrderWithFrozenPricesAndTotal() {
    Order order = Order.place(ORDER, "cliente-lucia", List.of(coffee(2), oil(1)));

    assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
    assertThat(order.total()).isEqualByComparingTo("47.70");
    assertThat(order.pendingChanges())
        .singleElement()
        .isInstanceOfSatisfying(
            OrderPlaced.class,
            placed -> {
              assertThat(placed.getOrderId()).isEqualTo(ORDER);
              assertThat(placed.getCustomerId()).isEqualTo("cliente-lucia");
              assertThat(placed.getLines()).hasSize(2);
              assertThat(placed.getTotal()).isEqualByComparingTo("47.70");
              assertThat(placed.getCurrency()).isEqualTo("EUR");
            });
  }

  @Test
  void anOrderNeedsAtLeastOneLine() {
    assertThatThrownBy(() -> Order.place(ORDER, "cliente-lucia", List.of()))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "empty-order");
  }

  @Test
  void anOrderHasAtMostTwentyLines() {
    List<PricedLine> lines = new ArrayList<>();
    IntStream.range(0, 21)
        .forEach(
            i ->
                lines.add(
                    new PricedLine(
                        UUID.randomUUID(), "SKU-" + i, "P" + i, 1, BigDecimal.ONE, "EUR")));

    assertThatThrownBy(() -> Order.place(ORDER, "cliente-lucia", lines))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "too-many-lines");
  }

  @Test
  void quantitiesGoFromOneToNinetyNine() {
    assertThatThrownBy(() -> Order.place(ORDER, "cliente-lucia", List.of(coffee(0))))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "invalid-quantity");
    assertThatThrownBy(() -> Order.place(ORDER, "cliente-lucia", List.of(coffee(100))))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "invalid-quantity");
  }

  @Test
  void eachProductAppearsOnce() {
    assertThatThrownBy(() -> Order.place(ORDER, "cliente-lucia", List.of(coffee(1), coffee(2))))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "duplicate-product");
  }

  @Test
  void allLinesShareOneCurrency() {
    PricedLine dollars = new PricedLine(OIL, "ACE-002", "Aceite", 1, new BigDecimal("9.90"), "USD");

    assertThatThrownBy(() -> Order.place(ORDER, "cliente-lucia", List.of(coffee(1), dollars)))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "mixed-currencies");
  }

  private static Order confirmed() {
    Order order = Order.place(ORDER, "cliente-lucia", List.of(coffee(1)));
    order.confirm(PAYMENT);
    order.markCommitted();
    return order;
  }

  @Test
  void aSuccessfulCheckoutConfirmsTheOrder() {
    Order order = Order.place(ORDER, "cliente-lucia", List.of(coffee(1)));
    order.markCommitted();

    order.confirm(PAYMENT);
    order.confirm(PAYMENT);

    assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.pendingChanges())
        .singleElement()
        .isInstanceOfSatisfying(
            OrderConfirmed.class, e -> assertThat(e.getPaymentId()).isEqualTo(PAYMENT));
  }

  @Test
  void aFailedCheckoutRejectsTheOrderWithTheReason() {
    Order order = Order.place(ORDER, "cliente-lucia", List.of(coffee(1)));
    order.markCommitted();

    order.reject("out-of-stock", "Sin stock de CAF-001");
    order.reject("out-of-stock", "Otra vez");

    assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
    assertThat(order.pendingChanges())
        .singleElement()
        .isInstanceOfSatisfying(
            OrderRejected.class,
            e -> {
              assertThat(e.getReason()).isEqualTo("out-of-stock");
              assertThat(e.getDetail()).isEqualTo("Sin stock de CAF-001");
            });
  }

  @Test
  void theCheckoutOutcomeIsFinal() {
    Order rejected = Order.place(ORDER, "cliente-lucia", List.of(coffee(1)));
    rejected.reject("out-of-stock", "Sin stock");

    assertThatThrownBy(() -> rejected.confirm(PAYMENT)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> confirmed().reject("out-of-stock", "tarde"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aConfirmedOrderCanBeCancelledWithWhoAndWhy() {
    Order order = confirmed();

    order.cancel("cliente-lucia", "Me he equivocado de talla");

    assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.pendingChanges())
        .singleElement()
        .isInstanceOfSatisfying(
            OrderCancelled.class,
            cancelled -> {
              assertThat(cancelled.getReason()).isEqualTo("Me he equivocado de talla");
              assertThat(cancelled.getCancelledBy()).isEqualTo("cliente-lucia");
            });
  }

  @Test
  void cancellingTwiceChangesNothing() {
    Order order = confirmed();
    order.cancel("cliente-lucia", "Ya no lo quiero");
    order.markCommitted();

    order.cancel("cliente-lucia", "Otra vez");

    assertThat(order.pendingChanges()).isEmpty();
  }

  @Test
  void anOrderInCheckoutOrRejectedCannotBeCancelled() {
    Order placed = Order.place(ORDER, "cliente-lucia", List.of(coffee(1)));
    Order rejected = Order.place(ORDER, "cliente-lucia", List.of(coffee(1)));
    rejected.reject("card-limit-exceeded", "Tarjeta");

    assertThatThrownBy(() -> placed.cancel("cliente-lucia", "prisa"))
        .isInstanceOf(ConflictException.class)
        .hasFieldOrPropertyWithValue("code", "checkout-in-progress");
    assertThatThrownBy(() -> rejected.cancel("cliente-lucia", "prisa"))
        .isInstanceOf(ConflictException.class)
        .hasFieldOrPropertyWithValue("code", "order-rejected");
  }

  @Test
  void statusesOnlyMoveForward() {
    assertThat(OrderStatus.PLACED.isBefore(OrderStatus.CONFIRMED)).isTrue();
    assertThat(OrderStatus.CONFIRMED.isBefore(OrderStatus.CANCELLED)).isTrue();
    assertThat(OrderStatus.REJECTED.isBefore(OrderStatus.CONFIRMED)).isFalse();
    assertThat(OrderStatus.CANCELLED.isBefore(OrderStatus.PLACED)).isFalse();
  }

  @Test
  void replayingTheHistoryRebuildsTheSameState() {
    Order original = Order.place(ORDER, "cliente-lucia", List.of(coffee(2)));
    original.confirm(PAYMENT);
    original.cancel("cliente-lucia", "Cambio de idea");

    Order replayed = new Order();
    replayed.replay(original.pendingChanges());

    assertThat(replayed.id()).isEqualTo(ORDER.toString());
    assertThat(replayed.customerId()).isEqualTo("cliente-lucia");
    assertThat(replayed.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(replayed.paymentId()).isEqualTo(PAYMENT);
    assertThat(replayed.total()).isEqualByComparingTo("37.80");
    assertThat(replayed.version()).isEqualTo(3);
  }
}
