package com.borjaglez.shop.notifications.domain;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A notice for a customer about one of their orders. It is created from an event; if the event
 * arrives before the order's {@code OrderPlaced} (Kafka only keeps order per message type), the
 * notice waits without customer and is worded once the owner is known.
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

  private static final Map<String, String> REASONS =
      Map.of(
          "out-of-stock", "there was not enough stock left",
          "card-limit-exceeded", "the amount exceeds your card limit",
          "inventory-unavailable", "inventory did not respond in time",
          "payment-unavailable", "the payment service did not respond in time",
          "voided", "the payment was voided before it was authorized");

  /** Id of the event that caused it: a redelivered event finds it and adds nothing. */
  @Id
  @Column(name = "event_id", length = 64)
  private String eventId;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "customer_id", length = 64)
  private String customerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private NotificationKind kind;

  /** Rejection reason code or cancellation reason. */
  @Column(length = 200)
  private String detail;

  /** Refunded amount, for refunds. */
  @Column(precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(length = 3)
  private String currency;

  @Column(length = 120)
  private String title;

  @Column(length = 300)
  private String body;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  @Column(name = "read_at")
  private OffsetDateTime readAt;

  public static Notification of(
      String eventId,
      UUID orderId,
      NotificationKind kind,
      String detail,
      BigDecimal amount,
      String currency,
      OffsetDateTime occurredAt) {
    Notification notification = new Notification();
    notification.eventId = eventId;
    notification.orderId = orderId;
    notification.kind = kind;
    notification.detail = detail;
    notification.amount = amount;
    notification.currency = currency;
    notification.occurredAt = occurredAt;
    return notification;
  }

  /** Whether it can be shown: its customer is known. */
  public boolean isAddressed() {
    return customerId != null;
  }

  /** Addresses the notice to the order's owner and words it. */
  public void addressTo(OrderOwner owner) {
    if (!owner.getOrderId().equals(orderId)) {
      throw new IllegalArgumentException("Owner of " + owner.getOrderId() + " for " + orderId);
    }
    customerId = owner.getCustomerId();
    String total = money(owner.getTotal(), owner.getCurrency());
    switch (kind) {
      case ORDER_CONFIRMED -> {
        title = "Order confirmed";
        body = "Your order of " + total + " is confirmed: stock reserved and payment authorized.";
      }
      case ORDER_REJECTED -> {
        title = "Order rejected";
        body =
            "We could not complete your order of "
                + total
                + ": "
                + (detail == null
                    ? "checkout could not be completed"
                    : REASONS.getOrDefault(detail, detail))
                + ". You have not been charged.";
      }
      case ORDER_CANCELLED -> {
        title = "Order cancelled";
        body = "You cancelled your order of " + total + ".";
      }
      case PAYMENT_REFUNDED -> {
        title = "Refund issued";
        body = "We refunded " + money(amount, currency) + " to you.";
      }
    }
  }

  public boolean isRead() {
    return readAt != null;
  }

  public void markRead(OffsetDateTime at) {
    if (readAt == null) {
      readAt = at;
    }
  }

  private static String money(BigDecimal value, String currencyCode) {
    NumberFormat format = NumberFormat.getCurrencyInstance(Locale.ENGLISH);
    format.setCurrency(Currency.getInstance(currencyCode));
    return format.format(value);
  }
}
