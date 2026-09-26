package com.borjaglez.shop.orders.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.shop.contracts.orders.OrderCancelled;
import com.borjaglez.shop.contracts.orders.OrderConfirmed;
import com.borjaglez.shop.contracts.orders.OrderLine;
import com.borjaglez.shop.contracts.orders.OrderPlaced;
import com.borjaglez.shop.contracts.orders.OrderRejected;
import com.borjaglez.shop.eskit.EventSourcedAggregate;
import com.borjaglez.shop.support.error.BusinessRuleViolationException;
import com.borjaglez.shop.support.error.ConflictException;

/**
 * An order, stored as its stream of events. Its state is never saved directly: it is whatever the
 * history says, rebuilt by {@link #when(Event)}.
 */
public class Order extends EventSourcedAggregate {

  /** Stream type of orders in the event store. */
  public static final String STREAM_TYPE = "order";

  static final int MAX_LINES = 20;
  static final int MAX_QUANTITY = 99;

  private UUID orderId;
  private String customerId;
  private List<OrderLine> lines = List.of();
  private BigDecimal total = BigDecimal.ZERO;
  private String currency;
  private OrderStatus status;
  private UUID paymentId;

  /** Places an order at the given prices. */
  public static Order place(UUID orderId, String customerId, List<PricedLine> lines) {
    Objects.requireNonNull(orderId, "orderId must not be null");
    validate(lines);
    BigDecimal total =
        lines.stream().map(PricedLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    Order order = new Order();
    order.apply(
        new OrderPlaced(
            orderId,
            customerId,
            lines.stream()
                .map(
                    l ->
                        new OrderLine(
                            l.productId(), l.sku(), l.name(), l.quantity(), l.unitPrice()))
                .toList(),
            total,
            lines.getFirst().currency()));
    return order;
  }

  /** The checkout succeeded. Confirming again changes nothing. */
  public void confirm(UUID paymentId) {
    if (status == OrderStatus.CONFIRMED) {
      return;
    }
    requireInCheckout("confirm");
    apply(new OrderConfirmed(orderId, paymentId));
  }

  /** The checkout failed and was undone. Rejecting again changes nothing. */
  public void reject(String reason, String detail) {
    if (status == OrderStatus.REJECTED) {
      return;
    }
    requireInCheckout("reject");
    apply(new OrderRejected(orderId, reason, detail));
  }

  /**
   * Cancels a confirmed order; the checkout saga then refunds it and releases its stock. Cancelling
   * an already cancelled order changes nothing.
   */
  public void cancel(String cancelledBy, String reason) {
    switch (status) {
      case CANCELLED -> {
        // Already cancelled: nothing to do.
      }
      case PLACED ->
          throw new ConflictException(
              "checkout-in-progress",
              "The order is still being checked out; it can be cancelled once confirmed");
      case REJECTED ->
          throw new ConflictException("order-rejected", "A rejected order cannot be cancelled");
      case CONFIRMED -> apply(new OrderCancelled(orderId, reason, cancelledBy));
    }
  }

  private void requireInCheckout(String action) {
    if (status != OrderStatus.PLACED) {
      throw new IllegalStateException("Cannot " + action + " order " + orderId + " in " + status);
    }
  }

  public boolean belongsTo(String customer) {
    return customerId.equals(customer);
  }

  @Override
  public String id() {
    return orderId.toString();
  }

  public String customerId() {
    return customerId;
  }

  /** The authorized payment, once confirmed. */
  public UUID paymentId() {
    return paymentId;
  }

  public OrderStatus status() {
    return status;
  }

  public BigDecimal total() {
    return total;
  }

  public String currency() {
    return currency;
  }

  public List<OrderLine> lines() {
    return lines;
  }

  @Override
  protected void when(Event event) {
    switch (event) {
      case OrderPlaced placed -> {
        orderId = placed.getOrderId();
        customerId = placed.getCustomerId();
        lines = List.copyOf(placed.getLines());
        total = placed.getTotal();
        currency = placed.getCurrency();
        status = OrderStatus.PLACED;
      }
      case OrderConfirmed confirmed -> {
        paymentId = confirmed.getPaymentId();
        status = OrderStatus.CONFIRMED;
      }
      case OrderRejected rejected -> status = OrderStatus.REJECTED;
      case OrderCancelled cancelled -> status = OrderStatus.CANCELLED;
      default -> throw new IllegalArgumentException("Unexpected event " + event.getClass());
    }
  }

  private static void validate(List<PricedLine> lines) {
    if (lines == null || lines.isEmpty()) {
      throw new BusinessRuleViolationException(
          "empty-order", "An order needs at least one product");
    }
    if (lines.size() > MAX_LINES) {
      throw new BusinessRuleViolationException(
          "too-many-lines", "An order can have at most " + MAX_LINES + " different products");
    }
    var seen = new HashSet<UUID>();
    for (PricedLine line : lines) {
      if (line.quantity() < 1 || line.quantity() > MAX_QUANTITY) {
        throw new BusinessRuleViolationException(
            "invalid-quantity",
            "Quantities go from 1 to " + MAX_QUANTITY + " (" + line.sku() + ")");
      }
      if (!seen.add(line.productId())) {
        throw new BusinessRuleViolationException(
            "duplicate-product", "Product " + line.sku() + " appears more than once");
      }
      if (!line.currency().equals(lines.getFirst().currency())) {
        throw new BusinessRuleViolationException(
            "mixed-currencies", "All products of an order must be priced in the same currency");
      }
    }
  }
}
