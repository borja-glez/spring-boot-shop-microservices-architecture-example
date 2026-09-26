package com.borjaglez.shop.orders.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Read model of an order, built from the order events received over Kafka.
 *
 * <p>Events are keyed by message type on the shared topic, so ordering is guaranteed only within
 * one event type; a confirmation or a cancellation can arrive before the order it refers to. The
 * view is therefore created by whichever event comes first, and the status only moves forward
 * ({@link OrderStatus#isBefore}): a late {@code OrderPlaced} fills in the details of an order
 * already known to be confirmed or cancelled without reopening it. Such a row has no customer until
 * then, so no customer lists it.
 */
@Entity
@Table(name = "order_view")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderView {

  @Id
  @Column(name = "order_id")
  private UUID orderId;

  @Column(name = "customer_id", length = 64)
  private String customerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderStatus status;

  @Column(precision = 12, scale = 2)
  private BigDecimal total;

  @Column(length = 3)
  private String currency;

  @Column(name = "line_count", nullable = false)
  private int lineCount;

  @Column(name = "cancel_reason", length = 200)
  private String cancelReason;

  @Column(name = "payment_id")
  private UUID paymentId;

  @Column(name = "rejection_reason", length = 40)
  private String rejectionReason;

  @Column(name = "rejection_detail", length = 500)
  private String rejectionDetail;

  @Column(name = "placed_at")
  private OffsetDateTime placedAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** Optimistic lock: concurrent consumers retry instead of overwriting each other. */
  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @ElementCollection
  @CollectionTable(name = "order_view_line", joinColumns = @JoinColumn(name = "order_id"))
  @OrderColumn(name = "line_no")
  private List<OrderViewLine> lines = new ArrayList<>();

  /** An order not seen yet; the first event applied to it decides what it is. */
  public static OrderView unknown(UUID orderId) {
    OrderView view = new OrderView();
    view.orderId = orderId;
    return view;
  }

  public void placed(
      String customerId,
      List<OrderViewLine> lines,
      BigDecimal total,
      String currency,
      OffsetDateTime at) {
    this.customerId = customerId;
    this.lines.clear();
    this.lines.addAll(lines);
    this.lineCount = lines.size();
    this.total = total;
    this.currency = currency;
    this.placedAt = at;
    if (status == null) {
      status = OrderStatus.PLACED;
    }
    touch(at);
  }

  public void confirmed(UUID payment, OffsetDateTime at) {
    paymentId = payment;
    advanceTo(OrderStatus.CONFIRMED);
    touch(at);
  }

  public void rejected(String reason, String detail, OffsetDateTime at) {
    rejectionReason = reason;
    rejectionDetail = detail;
    advanceTo(OrderStatus.REJECTED);
    touch(at);
  }

  public void cancelled(String reason, OffsetDateTime at) {
    cancelReason = reason;
    advanceTo(OrderStatus.CANCELLED);
    touch(at);
  }

  /** Statuses only move forward, whatever order the events arrive in. */
  private void advanceTo(OrderStatus next) {
    if (status == null || status.isBefore(next)) {
      status = next;
    }
  }

  public boolean belongsTo(String customer) {
    return customer.equals(customerId);
  }

  private void touch(OffsetDateTime at) {
    if (updatedAt == null || at.isAfter(updatedAt)) {
      updatedAt = at;
    }
  }
}
