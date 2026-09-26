package com.borjaglez.shop.payments.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.shop.contracts.payments.PaymentAuthorized;
import com.borjaglez.shop.contracts.payments.PaymentDeclined;
import com.borjaglez.shop.contracts.payments.PaymentRefunded;
import com.borjaglez.shop.eskit.EventSourcedAggregate;

/**
 * The payment of one order, stored as its events. The stream id is the order id, so an order can
 * never be charged twice.
 */
public class Payment extends EventSourcedAggregate {

  /** Stream type of payments in the event store. */
  public static final String STREAM_TYPE = "payment";

  /** The card cannot pay this much. */
  public static final String CARD_LIMIT_EXCEEDED = "card-limit-exceeded";

  /** The checkout was undone before the payment was requested. */
  public static final String VOIDED = "voided";

  private UUID paymentId;
  private UUID orderId;
  private String customerId;
  private BigDecimal amount;
  private String currency;
  private PaymentStatus status;
  private String reason;

  /** Authorizes the amount, or declines it when it is above the card limit. */
  public static Payment authorize(
      UUID orderId, String customerId, BigDecimal amount, String currency, BigDecimal cardLimit) {
    Objects.requireNonNull(orderId, "orderId must not be null");
    if (amount == null || amount.signum() <= 0) {
      throw new IllegalArgumentException("A payment needs a positive amount");
    }
    Payment payment = new Payment();
    UUID paymentId = UUID.randomUUID();
    if (amount.compareTo(cardLimit) > 0) {
      payment.apply(
          new PaymentDeclined(
              paymentId, orderId, customerId, amount, currency, CARD_LIMIT_EXCEEDED));
    } else {
      payment.apply(new PaymentAuthorized(paymentId, orderId, customerId, amount, currency));
    }
    return payment;
  }

  /**
   * An order whose checkout was undone before its payment was requested. Recording it stops a late
   * authorization, still travelling after a timeout, from charging the customer.
   */
  public static Payment voided(UUID orderId) {
    Payment payment = new Payment();
    payment.apply(
        new PaymentDeclined(UUID.randomUUID(), orderId, null, BigDecimal.ZERO, null, VOIDED));
    return payment;
  }

  /** Refunds an authorized payment. Returns whether anything was refunded. */
  public boolean refund() {
    if (status != PaymentStatus.AUTHORIZED) {
      return false;
    }
    apply(new PaymentRefunded(paymentId, orderId, amount, currency));
    return true;
  }

  /** Whether the amount was authorized, even if it was refunded afterwards. */
  public boolean wasAuthorized() {
    return status == PaymentStatus.AUTHORIZED || status == PaymentStatus.REFUNDED;
  }

  @Override
  public String id() {
    return orderId.toString();
  }

  public UUID paymentId() {
    return paymentId;
  }

  public UUID orderId() {
    return orderId;
  }

  public String customerId() {
    return customerId;
  }

  public BigDecimal amount() {
    return amount;
  }

  public String currency() {
    return currency;
  }

  public PaymentStatus status() {
    return status;
  }

  public String reason() {
    return reason;
  }

  @Override
  protected void when(Event event) {
    switch (event) {
      case PaymentAuthorized authorized -> {
        paymentId = authorized.getPaymentId();
        orderId = authorized.getOrderId();
        customerId = authorized.getCustomerId();
        amount = authorized.getAmount();
        currency = authorized.getCurrency();
        status = PaymentStatus.AUTHORIZED;
      }
      case PaymentDeclined declined -> {
        paymentId = declined.getPaymentId();
        orderId = declined.getOrderId();
        customerId = declined.getCustomerId();
        amount = declined.getAmount();
        currency = declined.getCurrency();
        status = PaymentStatus.DECLINED;
        reason = declined.getReason();
      }
      case PaymentRefunded refunded -> status = PaymentStatus.REFUNDED;
      default -> throw new IllegalArgumentException("Unexpected event " + event.getClass());
    }
  }
}
