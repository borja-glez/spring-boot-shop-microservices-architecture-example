package com.borjaglez.shop.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.orders.domain.CheckoutSaga.Mode;
import com.borjaglez.shop.orders.domain.CheckoutSaga.State;

class CheckoutSagaTest {

  private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-09-25T10:00:00Z");
  private static final UUID ORDER = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
  private static final UUID PAYMENT = UUID.fromString("5b0f6c2e-1d3a-4c55-9e0b-7a1f2c3d4e5f");
  private static final SagaRetryPolicy RETRIES =
      new SagaRetryPolicy(3, 5, Duration.ofSeconds(1), Duration.ofSeconds(4));

  private static CheckoutSaga started() {
    return CheckoutSaga.start(ORDER, "cliente-lucia", T0);
  }

  private static CheckoutSaga confirmed() {
    CheckoutSaga saga = started();
    saga.stockReserved(T0);
    saga.paymentAuthorized(PAYMENT, T0);
    saga.stepDone(T0);
    return saga;
  }

  @Test
  void aNewSagaReservesStockFirstAndIsDueRightAway() {
    CheckoutSaga saga = started();

    assertThat(saga.getStep()).isEqualTo(CheckoutStep.RESERVE_STOCK);
    assertThat(saga.getState()).isEqualTo(State.RUNNING);
    assertThat(saga.getMode()).isEqualTo(Mode.CHECKOUT);
    assertThat(saga.isDue(T0)).isTrue();
    assertThat(saga.getLog()).isEmpty();
  }

  @Test
  void theHappyPathReservesChargesAndConfirms() {
    CheckoutSaga saga = started();

    saga.stockReserved(T0.plusSeconds(1));
    assertThat(saga.getStep()).isEqualTo(CheckoutStep.AUTHORIZE_PAYMENT);
    saga.paymentAuthorized(PAYMENT, T0.plusSeconds(2));
    assertThat(saga.getStep()).isEqualTo(CheckoutStep.CONFIRM_ORDER);
    assertThat(saga.getPaymentId()).isEqualTo(PAYMENT);
    saga.stepDone(T0.plusSeconds(3));

    assertThat(saga.getStep()).isEqualTo(CheckoutStep.DONE);
    assertThat(saga.getState()).isEqualTo(State.COMPLETED);
    assertThat(saga.isDue(T0.plusDays(1))).isFalse();
    assertThat(saga.getLog())
        .extracting(SagaStepLog::getStep, SagaStepLog::getOutcome)
        .containsExactly(
            tuple(CheckoutStep.RESERVE_STOCK, StepOutcome.SUCCEEDED),
            tuple(CheckoutStep.AUTHORIZE_PAYMENT, StepOutcome.SUCCEEDED),
            tuple(CheckoutStep.CONFIRM_ORDER, StepOutcome.SUCCEEDED));
  }

  @Test
  void aShortageReleasesJustInCaseAndRejects() {
    CheckoutSaga saga = started();

    saga.stockShort("CAF-001: 3 requested, 1 available", T0);

    assertThat(saga.getMode()).isEqualTo(Mode.REJECTING);
    assertThat(saga.getRejectionReason()).isEqualTo("out-of-stock");
    assertThat(saga.getRejectionDetail()).isEqualTo("CAF-001: 3 requested, 1 available");
    assertThat(saga.getStep()).isEqualTo(CheckoutStep.RELEASE_STOCK);
    saga.stepDone(T0);
    assertThat(saga.getStep()).isEqualTo(CheckoutStep.REJECT_ORDER);
    saga.stepDone(T0);
    assertThat(saga.getState()).isEqualTo(State.COMPLETED);
    assertThat(saga.getLog().getFirst().getOutcome()).isEqualTo(StepOutcome.DECLINED);
  }

  @Test
  void aDeclinedPaymentReleasesTheStockAndRejects() {
    CheckoutSaga saga = started();
    saga.stockReserved(T0);

    saga.paymentDeclined("card-limit-exceeded", T0);

    assertThat(saga.getRejectionReason()).isEqualTo("card-limit-exceeded");
    assertThat(saga.getStep()).isEqualTo(CheckoutStep.RELEASE_STOCK);
    saga.stepDone(T0);
    assertThat(saga.getStep()).isEqualTo(CheckoutStep.REJECT_ORDER);
  }

  @Test
  void technicalFailuresAreRetriedWithGrowingWaits() {
    CheckoutSaga saga = started();

    saga.attemptFailed("timeout", T0, RETRIES);
    assertThat(saga.getAttempts()).isEqualTo(1);
    assertThat(saga.getNextAttemptAt()).isEqualTo(T0.plusSeconds(1));
    assertThat(saga.isDue(T0)).isFalse();
    assertThat(saga.getLastError()).isEqualTo("timeout");

    saga.attemptFailed("timeout", T0, RETRIES);
    assertThat(saga.getNextAttemptAt()).isEqualTo(T0.plusSeconds(2));
    assertThat(saga.getLog())
        .extracting(SagaStepLog::getOutcome)
        .containsOnly(StepOutcome.RETRYING);

    saga.stockReserved(T0);
    assertThat(saga.getAttempts()).isZero();
    assertThat(saga.getLastError()).isNull();
  }

  @Test
  void waitsStopGrowingAtTheMaximum() {
    assertThat(RETRIES.backoff(1)).isEqualTo(Duration.ofSeconds(1));
    assertThat(RETRIES.backoff(3)).isEqualTo(Duration.ofSeconds(4));
    assertThat(RETRIES.backoff(10)).isEqualTo(Duration.ofSeconds(4));
    assertThat(RETRIES.backoff(100)).isEqualTo(Duration.ofSeconds(4));
  }

  @Test
  void anUnreachableInventoryRejectsTheOrderAfterReleasing() {
    CheckoutSaga saga = started();

    for (int i = 0; i < 3; i++) {
      saga.attemptFailed("timeout", T0, RETRIES);
    }

    assertThat(saga.getRejectionReason()).isEqualTo("inventory-unavailable");
    assertThat(saga.getStep()).isEqualTo(CheckoutStep.RELEASE_STOCK);
    assertThat(saga.getAttempts()).isZero();
    assertThat(saga.getLog().getLast().getOutcome()).isEqualTo(StepOutcome.GAVE_UP);
  }

  @Test
  void anUnreachablePaymentServiceVoidsThePaymentBeforeReleasing() {
    CheckoutSaga saga = started();
    saga.stockReserved(T0);

    for (int i = 0; i < 3; i++) {
      saga.attemptFailed("timeout", T0, RETRIES);
    }

    assertThat(saga.getRejectionReason()).isEqualTo("payment-unavailable");
    assertThat(saga.getStep()).isEqualTo(CheckoutStep.REFUND_PAYMENT);
    saga.stepDone(T0);
    assertThat(saga.getStep()).isEqualTo(CheckoutStep.RELEASE_STOCK);
    saga.stepDone(T0);
    assertThat(saga.getStep()).isEqualTo(CheckoutStep.REJECT_ORDER);
  }

  @Test
  void aCompensationThatKeepsFailingIsFlaggedButNeverGivenUp() {
    CheckoutSaga saga = started();
    saga.stockShort("CAF-001", T0);

    for (int i = 0; i < 7; i++) {
      saga.attemptFailed("broker down", T0, RETRIES);
    }

    assertThat(saga.getState()).isEqualTo(State.STUCK);
    assertThat(saga.getStep()).isEqualTo(CheckoutStep.RELEASE_STOCK);
    assertThat(saga.getNextAttemptAt()).isEqualTo(T0.plusSeconds(4));
    assertThat(saga.isDue(T0.plusSeconds(4))).isTrue();
    assertThat(saga.getLog())
        .extracting(SagaStepLog::getOutcome)
        .doesNotContain(StepOutcome.GAVE_UP);

    saga.stepDone(T0);
    assertThat(saga.getState()).isEqualTo(State.RUNNING);
    assertThat(saga.getStep()).isEqualTo(CheckoutStep.REJECT_ORDER);
  }

  @Test
  void cancellingAConfirmedOrderRefundsAndReleases() {
    CheckoutSaga saga = confirmed();

    saga.cancel(T0.plusMinutes(5));

    assertThat(saga.getMode()).isEqualTo(Mode.CANCELLING);
    assertThat(saga.getState()).isEqualTo(State.RUNNING);
    assertThat(saga.getStep()).isEqualTo(CheckoutStep.REFUND_PAYMENT);
    assertThat(saga.isDue(T0.plusMinutes(5))).isTrue();
    saga.stepDone(T0);
    assertThat(saga.getStep()).isEqualTo(CheckoutStep.RELEASE_STOCK);
    saga.stepDone(T0);
    assertThat(saga.getStep()).isEqualTo(CheckoutStep.DONE);
    assertThat(saga.getState()).isEqualTo(State.COMPLETED);
  }

  @Test
  void onlyAConfirmedCheckoutCanBeCancelled() {
    CheckoutSaga running = started();
    CheckoutSaga rejected = started();
    rejected.stockShort("x", T0);
    rejected.stepDone(T0);
    rejected.stepDone(T0);

    assertThatThrownBy(() -> running.cancel(T0)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> rejected.cancel(T0)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void resultsMustMatchTheCurrentStep() {
    CheckoutSaga saga = started();

    assertThatThrownBy(() -> saga.paymentAuthorized(PAYMENT, T0))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> saga.paymentDeclined("x", T0))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> saga.stepDone(T0)).isInstanceOf(IllegalStateException.class);
    saga.stockReserved(T0);
    assertThatThrownBy(() -> saga.stockReserved(T0)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> saga.stockShort("x", T0)).isInstanceOf(IllegalStateException.class);
    CheckoutSaga done = confirmed();
    assertThatThrownBy(() -> done.stepDone(T0)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> done.attemptFailed("x", T0, RETRIES))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void longErrorsAreShortened() {
    CheckoutSaga saga = started();

    saga.attemptFailed("x".repeat(2000), T0, RETRIES);

    assertThat(saga.getLastError()).hasSize(500);
    assertThat(saga.getLog().getLast().getDetail()).hasSize(500);
  }
}
