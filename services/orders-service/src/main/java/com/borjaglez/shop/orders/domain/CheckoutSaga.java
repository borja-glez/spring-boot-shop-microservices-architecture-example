package com.borjaglez.shop.orders.domain;

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
 * Process manager of an order's checkout: which step comes next, how many times the current one
 * failed, and when to try again. It is stored, so a restart resumes every running checkout where it
 * was.
 *
 * <p>The saga decides; it never calls anything. The runner executes the current step and reports
 * the result through one of the methods below.
 *
 * <pre>
 * CHECKOUT    RESERVE_STOCK → AUTHORIZE_PAYMENT → CONFIRM_ORDER → DONE
 * REJECTING   [REFUND_PAYMENT →] RELEASE_STOCK → REJECT_ORDER → DONE
 * CANCELLING  REFUND_PAYMENT → RELEASE_STOCK → DONE
 * </pre>
 *
 * A rejected checkout always releases the stock, even after a "no stock" answer: an earlier attempt
 * that timed out may still reserve it later, and the release leaves a tombstone that stops it.
 */
@Entity
@Table(name = "checkout_saga")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckoutSaga {

  static final int MAX_ERROR = 500;

  /** Whether the saga still has work to do. */
  public enum State {
    RUNNING,
    COMPLETED,
    /**
     * A compensation or a local step keeps failing. The saga still retries at the longest wait, so
     * it finishes on its own once the other service is back, but someone should look at it.
     */
    STUCK
  }

  /** What the saga is working towards. */
  public enum Mode {
    CHECKOUT,
    REJECTING,
    CANCELLING
  }

  @Id
  @Column(name = "order_id")
  private UUID orderId;

  @Column(name = "customer_id", nullable = false, length = 64)
  private String customerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private State state;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Mode mode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private CheckoutStep step;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at")
  private OffsetDateTime nextAttemptAt;

  @Column(name = "last_error", length = MAX_ERROR)
  private String lastError;

  @Column(name = "payment_id")
  private UUID paymentId;

  @Column(name = "rejection_reason", length = 40)
  private String rejectionReason;

  @Column(name = "rejection_detail", length = MAX_ERROR)
  private String rejectionDetail;

  /** W3C {@code traceparent} of the request that started or cancelled the checkout. */
  @Column(name = "trace_parent", length = 55)
  private String traceParent;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** Two runners working on the same saga never both record a result. */
  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @ElementCollection
  @CollectionTable(name = "checkout_saga_step", joinColumns = @JoinColumn(name = "order_id"))
  @OrderColumn(name = "seq")
  private List<SagaStepLog> log = new ArrayList<>();

  /** A checkout that starts with the stock reservation, due right away. */
  public static CheckoutSaga start(UUID orderId, String customerId, OffsetDateTime at) {
    CheckoutSaga saga = new CheckoutSaga();
    saga.orderId = orderId;
    saga.customerId = customerId;
    saga.state = State.RUNNING;
    saga.mode = Mode.CHECKOUT;
    saga.createdAt = at;
    saga.moveTo(CheckoutStep.RESERVE_STOCK, at);
    return saga;
  }

  /**
   * The steps from now on belong to the trace of this request ({@code null} when there is none):
   * the one that placed the order, or the one that cancelled it.
   */
  public void followTrace(String traceParent) {
    this.traceParent = traceParent;
  }

  /** The customer cancelled the confirmed order: refund it and give the stock back. */
  public void cancel(OffsetDateTime at) {
    if (state != State.COMPLETED || mode != Mode.CHECKOUT) {
      throw new IllegalStateException("Only a confirmed checkout can be cancelled: " + orderId);
    }
    state = State.RUNNING;
    mode = Mode.CANCELLING;
    moveTo(CheckoutStep.REFUND_PAYMENT, at);
  }

  public boolean isDue(OffsetDateTime now) {
    return isActive() && !nextAttemptAt.isAfter(now);
  }

  /** Still has steps to run, retrying normally or stuck. */
  public boolean isActive() {
    return state == State.RUNNING || state == State.STUCK;
  }

  public void stockReserved(OffsetDateTime at) {
    require(CheckoutStep.RESERVE_STOCK);
    record(StepOutcome.SUCCEEDED, null, at);
    moveTo(CheckoutStep.AUTHORIZE_PAYMENT, at);
  }

  public void stockShort(String detail, OffsetDateTime at) {
    require(CheckoutStep.RESERVE_STOCK);
    record(StepOutcome.DECLINED, detail, at);
    reject("out-of-stock", detail, CheckoutStep.RELEASE_STOCK, at);
  }

  public void paymentAuthorized(UUID payment, OffsetDateTime at) {
    require(CheckoutStep.AUTHORIZE_PAYMENT);
    paymentId = payment;
    record(StepOutcome.SUCCEEDED, null, at);
    moveTo(CheckoutStep.CONFIRM_ORDER, at);
  }

  public void paymentDeclined(String reason, OffsetDateTime at) {
    require(CheckoutStep.AUTHORIZE_PAYMENT);
    record(StepOutcome.DECLINED, reason, at);
    reject(reason, "The payment was declined", CheckoutStep.RELEASE_STOCK, at);
  }

  /** The current confirm, reject, refund or release step is done. */
  public void stepDone(OffsetDateTime at) {
    if (!isActive()
        || step == CheckoutStep.RESERVE_STOCK
        || step == CheckoutStep.AUTHORIZE_PAYMENT) {
      throw new IllegalStateException("Step " + step + " of " + orderId + " needs a result");
    }
    record(StepOutcome.SUCCEEDED, null, at);
    moveTo(nextAfter(step), at);
  }

  /**
   * The current step failed for a technical reason (timeout, broker down, remote error). It runs
   * again later; after too many failures a forward step turns into a rejection. Any other step
   * cannot be given up, since that would leave stock held or a payment taken: the saga is marked
   * stuck and keeps retrying at the longest wait.
   */
  public void attemptFailed(String error, OffsetDateTime at, SagaRetryPolicy policy) {
    if (!isActive()) {
      throw new IllegalStateException("Saga of " + orderId + " is " + state);
    }
    attempts++;
    lastError = shortened(error);
    updatedAt = at;
    if (attempts < policy.limitFor(step)) {
      record(StepOutcome.RETRYING, error, at);
      nextAttemptAt = at.plus(policy.backoff(attempts));
      return;
    }
    if (step != CheckoutStep.RESERVE_STOCK && step != CheckoutStep.AUTHORIZE_PAYMENT) {
      record(StepOutcome.RETRYING, error, at);
      state = State.STUCK;
      nextAttemptAt = at.plus(policy.maxBackoff());
      return;
    }
    record(StepOutcome.GAVE_UP, error, at);
    switch (step) {
      case RESERVE_STOCK ->
          reject(
              "inventory-unavailable",
              "The inventory did not answer",
              CheckoutStep.RELEASE_STOCK,
              at);
      case AUTHORIZE_PAYMENT ->
          reject(
              "payment-unavailable",
              "The payment service did not answer",
              CheckoutStep.REFUND_PAYMENT,
              at);
      default -> throw new IllegalStateException("Unexpected forward step " + step);
    }
  }

  private void reject(String reason, String detail, CheckoutStep compensation, OffsetDateTime at) {
    mode = Mode.REJECTING;
    rejectionReason = reason;
    rejectionDetail = shortened(detail);
    moveTo(compensation, at);
  }

  private CheckoutStep nextAfter(CheckoutStep done) {
    return switch (done) {
      case REFUND_PAYMENT -> CheckoutStep.RELEASE_STOCK;
      case RELEASE_STOCK -> mode == Mode.CANCELLING ? CheckoutStep.DONE : CheckoutStep.REJECT_ORDER;
      default -> CheckoutStep.DONE;
    };
  }

  private void moveTo(CheckoutStep next, OffsetDateTime at) {
    step = next;
    attempts = 0;
    lastError = null;
    updatedAt = at;
    if (next == CheckoutStep.DONE) {
      state = State.COMPLETED;
      nextAttemptAt = null;
    } else {
      state = State.RUNNING;
      nextAttemptAt = at;
    }
  }

  private void require(CheckoutStep expected) {
    if (!isActive() || step != expected) {
      throw new IllegalStateException(
          "Saga of " + orderId + " is at " + step + " (" + state + "), not " + expected);
    }
  }

  private void record(StepOutcome outcome, String detail, OffsetDateTime at) {
    log.add(new SagaStepLog(step, outcome, shortened(detail), at));
  }

  private static String shortened(String text) {
    return text == null || text.length() <= MAX_ERROR ? text : text.substring(0, MAX_ERROR);
  }
}
