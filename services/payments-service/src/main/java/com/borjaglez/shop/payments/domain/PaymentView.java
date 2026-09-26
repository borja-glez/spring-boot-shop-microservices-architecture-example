package com.borjaglez.shop.payments.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
 * Read model of a payment. Unlike the orders read model, it is written in the same transaction as
 * the payment's events: the backoffice sees a payment the moment it exists, at the price of a write
 * to one more table per command.
 */
@Entity
@Table(name = "payment_view")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentView {

  @Id
  @Column(name = "order_id")
  private UUID orderId;

  @Column(name = "payment_id", nullable = false)
  private UUID paymentId;

  /** Unknown for a voided payment. */
  @Column(name = "customer_id", length = 64)
  private String customerId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  @Column(length = 60)
  private String reason;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public static PaymentView created(
      UUID orderId,
      UUID paymentId,
      String customerId,
      BigDecimal amount,
      String currency,
      PaymentStatus status,
      String reason,
      OffsetDateTime at) {
    PaymentView view = new PaymentView();
    view.orderId = orderId;
    view.paymentId = paymentId;
    view.customerId = customerId;
    view.amount = amount;
    view.currency = currency;
    view.status = status;
    view.reason = reason;
    view.createdAt = at;
    view.updatedAt = at;
    return view;
  }

  public void changed(PaymentStatus newStatus, OffsetDateTime at) {
    status = newStatus;
    updatedAt = at;
  }
}
