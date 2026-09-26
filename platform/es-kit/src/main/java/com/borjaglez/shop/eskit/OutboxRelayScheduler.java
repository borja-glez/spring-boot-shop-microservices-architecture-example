package com.borjaglez.shop.eskit;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Runs the relay on its own thread with a fixed delay, started after the context is ready and
 * stopped before the database goes away on shutdown.
 */
class OutboxRelayScheduler implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

  private final OutboxRelay relay;
  private final Duration interval;
  private ScheduledExecutorService executor;

  OutboxRelayScheduler(OutboxRelay relay, Duration interval) {
    this.relay = relay;
    this.interval = interval;
  }

  @Override
  public synchronized void start() {
    executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "outbox-relay"));
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
      relay.relayPending();
    } catch (RuntimeException e) {
      log.warn("Outbox relay run failed; retrying in {}: {}", interval, e.toString());
    } catch (Error e) {
      log.error("Outbox relay run failed with an error; retrying in {}", interval, e);
    }
  }

  @Override
  public synchronized void stop() {
    if (executor != null) {
      executor.shutdown();
      try {
        executor.awaitTermination(5, TimeUnit.SECONDS);
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
