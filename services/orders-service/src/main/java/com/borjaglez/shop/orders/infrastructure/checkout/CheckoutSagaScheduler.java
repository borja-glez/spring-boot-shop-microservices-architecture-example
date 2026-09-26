package com.borjaglez.shop.orders.infrastructure.checkout;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import com.borjaglez.shop.orders.application.checkout.CheckoutSagaRunner;

/**
 * Runs the checkout saga runner on its own thread with a fixed delay. It starts once the context is
 * ready, so a restarted service resumes the running checkouts, and stops before the database and
 * the brokers go away.
 */
class CheckoutSagaScheduler implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(CheckoutSagaScheduler.class);

  private final CheckoutSagaRunner runner;
  private final Duration interval;
  private ScheduledExecutorService executor;

  CheckoutSagaScheduler(CheckoutSagaRunner runner, Duration interval) {
    this.runner = runner;
    this.interval = interval;
  }

  @Override
  public synchronized void start() {
    executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "checkout-saga"));
    executor.scheduleWithFixedDelay(
        this::runSafely, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * A scheduled executor silently drops a task that throws, so nothing may escape, not even an
   * {@link Error}: the loop would stop for good while the service still looks healthy. In a native
   * image a missing reflection hint is such an error.
   */
  private void runSafely() {
    try {
      runner.runDue();
    } catch (RuntimeException e) {
      log.warn("Checkout saga run failed; retrying in {}: {}", interval, e.toString());
    } catch (Error e) {
      log.error("Checkout saga run failed with an error; retrying in {}", interval, e);
    }
  }

  @Override
  public synchronized void stop() {
    if (executor != null) {
      executor.shutdown();
      try {
        // A step may be waiting for a reply; give it the reply timeout to finish.
        executor.awaitTermination(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      executor = null;
    }
  }

  @Override
  public synchronized boolean isRunning() {
    return executor != null;
  }
}
