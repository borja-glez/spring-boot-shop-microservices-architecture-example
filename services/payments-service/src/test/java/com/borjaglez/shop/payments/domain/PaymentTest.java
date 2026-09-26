package com.borjaglez.shop.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.contracts.payments.PaymentAuthorized;
import com.borjaglez.shop.contracts.payments.PaymentDeclined;
import com.borjaglez.shop.contracts.payments.PaymentRefunded;

class PaymentTest {

  private static final BigDecimal LIMIT = new BigDecimal("300");
  private static final UUID ORDER = UUID.randomUUID();

  @Test
  void amountsWithinTheCardLimitAreAuthorized() {
    Payment payment =
        Payment.authorize(ORDER, "cliente-lucia", new BigDecimal("300.00"), "EUR", LIMIT);

    assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(payment.wasAuthorized()).isTrue();
    assertThat(payment.id()).isEqualTo(ORDER.toString());
    assertThat(payment.pendingChanges()).singleElement().isInstanceOf(PaymentAuthorized.class);
  }

  @Test
  void amountsAboveTheCardLimitAreDeclined() {
    Payment payment =
        Payment.authorize(ORDER, "cliente-lucia", new BigDecimal("300.01"), "EUR", LIMIT);

    assertThat(payment.status()).isEqualTo(PaymentStatus.DECLINED);
    assertThat(payment.wasAuthorized()).isFalse();
    assertThat(payment.reason()).isEqualTo(Payment.CARD_LIMIT_EXCEEDED);
    assertThat(payment.pendingChanges()).singleElement().isInstanceOf(PaymentDeclined.class);
  }

  @Test
  void anAuthorizedPaymentIsRefundedOnce() {
    Payment payment = Payment.authorize(ORDER, "cliente-lucia", BigDecimal.TEN, "EUR", LIMIT);

    assertThat(payment.refund()).isTrue();
    assertThat(payment.refund()).isFalse();

    assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(payment.wasAuthorized()).isTrue();
    assertThat(payment.pendingChanges()).last().isInstanceOf(PaymentRefunded.class);
  }

  @Test
  void aDeclinedOrVoidedPaymentHasNothingToRefund() {
    Payment declined =
        Payment.authorize(ORDER, "cliente-lucia", new BigDecimal("999"), "EUR", LIMIT);
    Payment voided = Payment.voided(ORDER);

    assertThat(declined.refund()).isFalse();
    assertThat(voided.refund()).isFalse();
    assertThat(voided.reason()).isEqualTo(Payment.VOIDED);
    assertThat(voided.customerId()).isNull();
  }

  @Test
  void thePaymentIsRebuiltFromItsEvents() {
    Payment original = Payment.authorize(ORDER, "cliente-lucia", BigDecimal.TEN, "EUR", LIMIT);
    original.refund();

    Payment replayed = new Payment();
    replayed.replay(original.pendingChanges());

    assertThat(replayed.status()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(replayed.paymentId()).isEqualTo(original.paymentId());
    assertThat(replayed.amount()).isEqualByComparingTo("10");
    assertThat(replayed.currency()).isEqualTo("EUR");
    assertThat(replayed.orderId()).isEqualTo(ORDER);
    assertThat(replayed.version()).isEqualTo(2);
  }

  @Test
  void aPaymentNeedsAnOrderAndAPositiveAmount() {
    assertThatThrownBy(() -> Payment.authorize(null, "c", BigDecimal.TEN, "EUR", LIMIT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Payment.authorize(ORDER, "c", BigDecimal.ZERO, "EUR", LIMIT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Payment.authorize(ORDER, "c", null, "EUR", LIMIT))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
