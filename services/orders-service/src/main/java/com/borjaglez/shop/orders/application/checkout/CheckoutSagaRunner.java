package com.borjaglez.shop.orders.application.checkout;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Advances every checkout saga whose next attempt is due. Each run claims its sagas first ({@link
 * DueCheckouts}), so any number of instances can run side by side without working on the same saga.
 */
@Component
public class CheckoutSagaRunner {

  private static final Logger log = LoggerFactory.getLogger(CheckoutSagaRunner.class);

  private final DueCheckouts dueCheckouts;
  private final CheckoutSagaProcessor processor;
  private final CheckoutProperties properties;
  private final Clock clock;

  public CheckoutSagaRunner(
      DueCheckouts dueCheckouts,
      CheckoutSagaProcessor processor,
      CheckoutProperties properties,
      Clock clock) {
    this.dueCheckouts = dueCheckouts;
    this.processor = processor;
    this.properties = properties;
    this.clock = clock;
  }

  /** Runs one step of each due saga. Returns how many steps ran. */
  public int runDue() {
    OffsetDateTime now = OffsetDateTime.now(clock);
    List<UUID> due = dueCheckouts.claim(properties.batchSize(), now, now.plus(properties.lease()));
    // Each step may wait up to the reply timeout for another service, so the sagas of a batch run
    // side by side: one slow or unreachable service does not hold back every other checkout.
    AtomicInteger ran = new AtomicInteger();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (UUID orderId : due) {
        executor.submit(() -> advance(orderId, ran));
      }
    }
    return ran.get();
  }

  private void advance(UUID orderId, AtomicInteger ran) {
    try {
      if (processor.advanceClaimed(orderId)) {
        ran.incrementAndGet();
      }
    } catch (RuntimeException e) {
      // Usually another runner recorded a result first (optimistic lock); it runs again later.
      log.warn("Could not advance the checkout of {}: {}", orderId, e.toString());
    } catch (Error e) {
      // The task runs through submit(): an error would stay in its Future, unseen, and the saga
      // would never move again without a trace in the logs.
      log.error("Could not advance the checkout of {}", orderId, e);
    }
  }
}
