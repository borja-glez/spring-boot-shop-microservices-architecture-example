package com.borjaglez.shop.orders.application.checkout;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.shop.contracts.inventory.ReservationLine;
import com.borjaglez.shop.contracts.inventory.StockReservation;
import com.borjaglez.shop.contracts.payments.PaymentAuthorization;
import com.borjaglez.shop.eskit.AggregateStore;
import com.borjaglez.shop.orders.domain.CheckoutSaga;
import com.borjaglez.shop.orders.domain.CheckoutSagaRepository;
import com.borjaglez.shop.orders.domain.CheckoutStep;
import com.borjaglez.shop.orders.domain.Order;
import com.borjaglez.shop.support.tracing.TraceCarrier;
import com.borjaglez.specrepository.core.Operators;

/**
 * Runs one step of a checkout saga and records what happened.
 *
 * <p>A remote step (reserve, authorize, refund, release) calls the other service <em>outside</em>
 * any transaction, since the call can take up to the reply timeout, and records the result in a
 * short transaction afterwards. If another runner recorded a result in the meantime, the saga's
 * version changed and this result is dropped: the remote commands are idempotent, so running one
 * twice is harmless.
 *
 * <p>A local step (confirm, reject) writes the order's event and moves the saga in one transaction.
 */
@Component
public class CheckoutSagaProcessor {

  private static final Logger log = LoggerFactory.getLogger(CheckoutSagaProcessor.class);

  private final CheckoutSagaRepository sagas;
  private final AggregateStore<Order> orders;
  private final InventoryGateway inventory;
  private final PaymentsGateway payments;
  private final TransactionTemplate transactions;
  private final CheckoutProperties properties;
  private final Clock clock;
  private final TraceCarrier traces;
  private final CheckoutMetrics metrics;

  public CheckoutSagaProcessor(
      CheckoutSagaRepository sagas,
      AggregateStore<Order> orders,
      InventoryGateway inventory,
      PaymentsGateway payments,
      TransactionTemplate transactions,
      CheckoutProperties properties,
      Clock clock,
      TraceCarrier traces,
      CheckoutMetrics metrics) {
    this.sagas = sagas;
    this.orders = orders;
    this.inventory = inventory;
    this.payments = payments;
    this.transactions = transactions;
    this.properties = properties;
    this.clock = clock;
    this.traces = traces;
    this.metrics = metrics;
  }

  /** Runs the current step of the order's saga if it is due. Returns whether it ran a step. */
  public boolean advance(UUID orderId) {
    return run(orderId, true);
  }

  /**
   * Runs the current step of a saga the runner claimed. The claim's lease pushed its next attempt
   * forward, so the saga is no longer due: it only has to be unfinished.
   */
  public boolean advanceClaimed(UUID orderId) {
    return run(orderId, false);
  }

  private boolean run(UUID orderId, boolean onlyIfDue) {
    Optional<CheckoutSaga> loaded = transactions.execute(status -> find(orderId));
    if (loaded.isEmpty() || !loaded.get().isActive() || (onlyIfDue && !loaded.get().isDue(now()))) {
      return false;
    }
    CheckoutSaga saga = loaded.get();
    // Every step, remote or local, continues the trace of the request that placed (or cancelled)
    // the order: the events a local step records carry it on to Kafka.
    traces.resume(
        trace(saga),
        "checkout " + saga.getStep(),
        () -> {
          if (saga.getStep().isRemote()) {
            runRemote(saga);
          } else {
            runLocal(saga);
          }
          return null;
        });
    return true;
  }

  private void runRemote(CheckoutSaga saga) {
    Consumer<CheckoutSaga> result;
    try {
      result = callRemote(saga);
    } catch (RuntimeException e) {
      log.warn(
          "Checkout step {} of {} failed: {}", saga.getStep(), saga.getOrderId(), e.toString());
      String error = describe(e);
      result = s -> s.attemptFailed(error, now(), properties.retryPolicy());
    }
    Consumer<CheckoutSaga> outcome = result;
    metrics.publish(transactions.execute(status -> recordIfUnchanged(saga, outcome)));
  }

  private static Map<String, String> trace(CheckoutSaga saga) {
    return saga.getTraceParent() == null
        ? Map.of()
        : Map.of(TraceCarrier.TRACEPARENT, saga.getTraceParent());
  }

  private Consumer<CheckoutSaga> callRemote(CheckoutSaga saga) {
    UUID orderId = saga.getOrderId();
    return switch (saga.getStep()) {
      case RESERVE_STOCK -> {
        StockReservation reservation = inventory.reserve(orderId, reservationLines(orderId));
        yield reservation.reserved()
            ? s -> s.stockReserved(now())
            : s -> s.stockShort(shortages(reservation), now());
      }
      case AUTHORIZE_PAYMENT -> {
        Order order = order(orderId);
        PaymentAuthorization authorization =
            payments.authorize(orderId, order.customerId(), order.total(), order.currency());
        yield authorization.authorized()
            ? s -> s.paymentAuthorized(authorization.paymentId(), now())
            : s -> s.paymentDeclined(authorization.reason(), now());
      }
      case RELEASE_STOCK -> {
        inventory.release(orderId);
        yield s -> s.stepDone(now());
      }
      case REFUND_PAYMENT -> {
        payments.refund(orderId);
        yield s -> s.stepDone(now());
      }
      default -> throw new IllegalStateException("Not a remote step: " + saga.getStep());
    };
  }

  private void runLocal(CheckoutSaga saga) {
    try {
      metrics.publish(transactions.execute(status -> runLocalStep(saga)));
    } catch (RuntimeException e) {
      log.warn(
          "Checkout step {} of {} failed: {}", saga.getStep(), saga.getOrderId(), e.toString());
      String error = describe(e);
      metrics.publish(
          transactions.execute(
              status ->
                  recordIfUnchanged(
                      saga, s -> s.attemptFailed(error, now(), properties.retryPolicy()))));
    }
  }

  private CheckoutMetrics.StepResult runLocalStep(CheckoutSaga saga) {
    CheckoutSaga fresh = unchanged(saga);
    if (fresh == null) {
      return null;
    }
    Order order = order(fresh.getOrderId());
    if (fresh.getStep() == CheckoutStep.CONFIRM_ORDER) {
      order.confirm(fresh.getPaymentId());
    } else {
      order.reject(fresh.getRejectionReason(), fresh.getRejectionDetail());
    }
    orders.save(order);
    fresh.stepDone(now());
    sagas.save(fresh);
    return CheckoutMetrics.capture(saga.getStep(), fresh);
  }

  /** Applies the outcome unless another runner moved the saga; returns what it recorded. */
  private CheckoutMetrics.StepResult recordIfUnchanged(
      CheckoutSaga seen, Consumer<CheckoutSaga> outcome) {
    CheckoutSaga fresh = unchanged(seen);
    if (fresh == null) {
      return null;
    }
    outcome.accept(fresh);
    sagas.save(fresh);
    return CheckoutMetrics.capture(seen.getStep(), fresh);
  }

  /** The saga as stored now, or {@code null} if another runner moved it since {@code seen}. */
  private CheckoutSaga unchanged(CheckoutSaga seen) {
    CheckoutSaga fresh = find(seen.getOrderId()).orElse(null);
    if (fresh == null
        || fresh.getStep() != seen.getStep()
        || !fresh.getRowVersion().equals(seen.getRowVersion())) {
      log.info("Checkout of {} moved on while running {}", seen.getOrderId(), seen.getStep());
      return null;
    }
    return fresh;
  }

  private Optional<CheckoutSaga> find(UUID orderId) {
    return sagas.query().where("orderId", Operators.EQUALS, orderId).findOne();
  }

  private Order order(UUID orderId) {
    return transactions.execute(
        status ->
            orders
                .load(orderId.toString())
                .orElseThrow(() -> new IllegalStateException("No order " + orderId)));
  }

  private List<ReservationLine> reservationLines(UUID orderId) {
    return order(orderId).lines().stream()
        .map(line -> new ReservationLine(line.productId(), line.sku(), line.quantity()))
        .toList();
  }

  private static String shortages(StockReservation reservation) {
    return reservation.shortages().stream()
        .map(s -> s.sku() + ": " + s.requested() + " requested, " + s.available() + " available")
        .collect(Collectors.joining("; "));
  }

  private static String describe(RuntimeException e) {
    return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock);
  }
}
