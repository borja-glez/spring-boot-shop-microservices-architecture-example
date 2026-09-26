package com.borjaglez.shop.orders.application.checkout;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.borjaglez.shop.orders.domain.CheckoutSaga;
import com.borjaglez.shop.orders.domain.CheckoutSagaRepository;
import com.borjaglez.shop.orders.domain.CheckoutStep;
import com.borjaglez.shop.orders.domain.SagaStepLog;
import com.borjaglez.specrepository.core.Operators;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Business metrics of the checkout saga:
 *
 * <ul>
 *   <li>{@code shop.checkout.steps}: attempts of each step by result ({@code succeeded}, {@code
 *       declined}, {@code retrying}, {@code gave-up});
 *   <li>{@code shop.checkout.completed}: finished sagas by outcome ({@code confirmed}, {@code
 *       rejected}, {@code cancelled}) and rejection reason;
 *   <li>{@code shop.checkout.duration}: time from order placed to confirmed or rejected;
 *   <li>{@code shop.checkout.sagas}: sagas still running or stuck, read from the database.
 * </ul>
 *
 * Results are captured inside the transaction that records them ({@link #capture}) and published
 * after it commits ({@link #publish}), so a rolled-back step is never counted.
 */
@Component
public class CheckoutMetrics implements MeterBinder {

  /** What one recorded step did, detached from the persistence context. */
  public record StepResult(
      CheckoutStep step, String result, String outcome, String reason, Duration duration) {}

  private final CheckoutSagaRepository sagas;
  private MeterRegistry meters;

  public CheckoutMetrics(CheckoutSagaRepository sagas) {
    this.sagas = sagas;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    this.meters = registry;
    for (CheckoutSaga.State state : List.of(CheckoutSaga.State.RUNNING, CheckoutSaga.State.STUCK)) {
      Gauge.builder("shop.checkout.sagas", () -> count(state))
          .description("Checkout sagas that still have work to do")
          .tag("state", state.name().toLowerCase(Locale.ROOT))
          .register(registry);
    }
  }

  /** Reads what the step just recorded on {@code saga}; call it inside the transaction. */
  public static StepResult capture(CheckoutStep step, CheckoutSaga saga) {
    List<SagaStepLog> log = saga.getLog();
    String result =
        log.isEmpty()
            ? "succeeded"
            : log.getLast().getOutcome().name().toLowerCase(Locale.ROOT).replace('_', '-');
    if (saga.getState() != CheckoutSaga.State.COMPLETED) {
      return new StepResult(step, result, null, null, null);
    }
    return switch (saga.getMode()) {
      case CHECKOUT -> new StepResult(step, result, "confirmed", "none", elapsed(saga));
      case REJECTING ->
          new StepResult(step, result, "rejected", saga.getRejectionReason(), elapsed(saga));
      case CANCELLING -> new StepResult(step, result, "cancelled", "none", null);
    };
  }

  /** Publishes a captured result; call it once the transaction committed. */
  public void publish(StepResult result) {
    if (meters == null || result == null) {
      return;
    }
    meters
        .counter("shop.checkout.steps", "step", result.step().name(), "result", result.result())
        .increment();
    if (result.outcome() != null) {
      meters
          .counter(
              "shop.checkout.completed", "outcome", result.outcome(), "reason", result.reason())
          .increment();
    }
    if (result.duration() != null) {
      meters.timer("shop.checkout.duration", "outcome", result.outcome()).record(result.duration());
    }
  }

  private static Duration elapsed(CheckoutSaga saga) {
    return Duration.between(saga.getCreatedAt(), saga.getUpdatedAt()).abs();
  }

  private double count(CheckoutSaga.State state) {
    try {
      return sagas.query().where("state", Operators.EQUALS, state).count();
    } catch (RuntimeException e) {
      return Double.NaN;
    }
  }
}
