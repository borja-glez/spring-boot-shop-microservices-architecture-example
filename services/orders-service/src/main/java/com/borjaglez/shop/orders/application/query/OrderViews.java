package com.borjaglez.shop.orders.application.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.borjaglez.shop.contracts.notifications.OrderNotice;
import com.borjaglez.shop.eskit.StoredEvent;
import com.borjaglez.shop.orders.domain.CheckoutSaga;
import com.borjaglez.shop.orders.domain.CheckoutStep;
import com.borjaglez.shop.orders.domain.Order;
import com.borjaglez.shop.orders.domain.OrderStatus;
import com.borjaglez.shop.orders.domain.OrderView;
import com.borjaglez.shop.orders.domain.OrderViewLine;
import com.borjaglez.shop.orders.domain.StepOutcome;
import com.fasterxml.jackson.annotation.JsonRawValue;

/** Shapes returned by the orders read side. */
public final class OrderViews {

  private OrderViews() {}

  /** An order in the customer's list. */
  public record OrderSummary(
      UUID orderId,
      OrderStatus status,
      BigDecimal total,
      String currency,
      int lineCount,
      OffsetDateTime placedAt,
      OffsetDateTime updatedAt) {}

  /** An order with its lines. */
  public record OrderDetail(
      UUID orderId,
      OrderStatus status,
      BigDecimal total,
      String currency,
      String cancelReason,
      UUID paymentId,
      String rejectionReason,
      String rejectionDetail,
      OffsetDateTime placedAt,
      OffsetDateTime updatedAt,
      List<LineView> lines) {}

  public record LineView(
      UUID productId,
      String sku,
      String name,
      int quantity,
      BigDecimal unitPrice,
      BigDecimal subtotal) {}

  /**
   * A cart priced with the current catalog prices and checked against the current stock.
   *
   * @param total sum of the lines that can be ordered
   * @param stockChecked whether the inventory answered; when not, only prices were checked
   * @param orderable whether every line can be ordered as it is
   */
  public record CartQuote(
      List<QuoteLine> lines,
      BigDecimal total,
      String currency,
      boolean stockChecked,
      boolean orderable) {}

  /**
   * A cart line as it would be ordered now.
   *
   * @param unitPrice {@code null} when the product is not for sale
   * @param available free units; {@code null} when unknown
   * @param problem why the line cannot be ordered as it is; {@code null} when it can
   */
  public record QuoteLine(
      UUID productId,
      String sku,
      String name,
      int quantity,
      BigDecimal unitPrice,
      BigDecimal subtotal,
      Integer available,
      QuoteProblem problem) {}

  /** Why a cart line cannot be ordered as it is. */
  public enum QuoteProblem {
    NOT_FOR_SALE,
    NOT_ENOUGH_STOCK
  }

  /**
   * The notices sent about an order.
   *
   * @param available whether the notifications service answered
   */
  public record OrderNoticesView(boolean available, List<OrderNotice> notices) {}

  /**
   * One event of an order's stream and the state the order had right after it.
   *
   * @param event the event as stored, serialized with its concrete type
   */
  public record HistoryEntry(
      long version,
      String eventType,
      OffsetDateTime occurredAt,
      OffsetDateTime publishedAt,
      Object event,
      OrderState stateAfter) {}

  public record OrderState(OrderStatus status, BigDecimal total, String currency, int lineCount) {}

  /** A row of the event store, payload and metadata included as JSON. */
  public record StoredEventView(
      long globalPosition,
      UUID eventId,
      String streamType,
      String streamId,
      Long version,
      String eventType,
      OffsetDateTime occurredAt,
      OffsetDateTime publishedAt,
      int publishAttempts,
      String lastError,
      @JsonRawValue String payload,
      @JsonRawValue String metadata) {}

  /** The checkout saga of an order: where it is and every attempt so far. */
  public record CheckoutView(
      UUID orderId,
      CheckoutSaga.State state,
      CheckoutSaga.Mode mode,
      CheckoutStep step,
      int attempts,
      OffsetDateTime nextAttemptAt,
      String lastError,
      String rejectionReason,
      String rejectionDetail,
      List<CheckoutStepView> steps) {}

  public record CheckoutStepView(
      CheckoutStep step, StepOutcome outcome, String detail, OffsetDateTime at) {}

  static CheckoutView checkout(CheckoutSaga saga) {
    return new CheckoutView(
        saga.getOrderId(),
        saga.getState(),
        saga.getMode(),
        saga.getStep(),
        saga.getAttempts(),
        saga.getNextAttemptAt(),
        saga.getLastError(),
        saga.getRejectionReason(),
        saga.getRejectionDetail(),
        saga.getLog().stream()
            .map(l -> new CheckoutStepView(l.getStep(), l.getOutcome(), l.getDetail(), l.getAt()))
            .toList());
  }

  static OrderSummary summary(OrderView view) {
    return new OrderSummary(
        view.getOrderId(),
        view.getStatus(),
        view.getTotal(),
        view.getCurrency(),
        view.getLineCount(),
        view.getPlacedAt(),
        view.getUpdatedAt());
  }

  static OrderDetail detail(OrderView view) {
    return new OrderDetail(
        view.getOrderId(),
        view.getStatus(),
        view.getTotal(),
        view.getCurrency(),
        view.getCancelReason(),
        view.getPaymentId(),
        view.getRejectionReason(),
        view.getRejectionDetail(),
        view.getPlacedAt(),
        view.getUpdatedAt(),
        view.getLines().stream().map(OrderViews::line).toList());
  }

  static OrderState state(Order order) {
    return new OrderState(order.status(), order.total(), order.currency(), order.lines().size());
  }

  static StoredEventView stored(StoredEvent row) {
    return new StoredEventView(
        row.getGlobalPosition(),
        row.getEventId(),
        row.getStreamType(),
        row.getStreamId(),
        row.getVersion(),
        row.getEventType(),
        row.getOccurredAt(),
        row.getPublishedAt(),
        row.getPublishAttempts(),
        row.getLastError(),
        row.getPayload(),
        row.getMetadata());
  }

  private static LineView line(OrderViewLine line) {
    return new LineView(
        line.getProductId(),
        line.getSku(),
        line.getName(),
        line.getQuantity(),
        line.getUnitPrice(),
        line.subtotal());
  }
}
