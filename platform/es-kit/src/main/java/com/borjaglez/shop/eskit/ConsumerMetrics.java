package com.borjaglez.shop.eskit;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.idempotency.Acquisition;
import com.borjaglez.cqrs.idempotency.IdempotencyStore;
import com.borjaglez.cqrs.idempotency.Idempotent;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.MiddlewareChain;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Metrics of the {@link Idempotent} event handlers, the consumers of the read models, keyed by
 * their handler id ({@code @Idempotent(name = ...)}).
 *
 * <p>{@code shop.consumer.events} counts the events of each consumer by outcome ({@code applied} or
 * {@code duplicate}), and {@code shop.consumer.lag} times how long after it happened an event
 * reached the consumer's view: the eventual-consistency window of that read model.
 *
 * <p>It is a middleware, so it knows the event being handled, and it wraps the idempotency store
 * ({@link #meter}), which knows what each handler did with it. Both run in the thread that
 * dispatches the event.
 */
public class ConsumerMetrics implements BusMiddleware {

  private final ThreadLocal<Event> handling = new ThreadLocal<>();
  private final Clock clock;
  private final MeterRegistry meters;

  public ConsumerMetrics(Clock clock, MeterRegistry meters) {
    this.clock = clock;
    this.meters = meters;
  }

  @Override
  public Object process(Object message, MiddlewareChain chain) throws Exception {
    if (!(message instanceof Event event)) {
      return chain.proceed(message);
    }
    Event outer = handling.get();
    handling.set(event);
    try {
      return chain.proceed(message);
    } finally {
      if (outer == null) {
        handling.remove();
      } else {
        handling.set(outer);
      }
    }
  }

  /** Wraps {@code store} so that what each handler does with an event is measured. */
  public IdempotencyStore meter(IdempotencyStore store) {
    return new IdempotencyStore() {

      @Override
      public Acquisition tryAcquire(String handlerId, String messageId) {
        Acquisition acquisition = store.tryAcquire(handlerId, messageId);
        if (acquisition == Acquisition.DUPLICATE) {
          count(handlerId, "duplicate");
        }
        return acquisition;
      }

      @Override
      public void complete(String handlerId, String messageId) {
        store.complete(handlerId, messageId);
        count(handlerId, "applied");
        Event event = handling.get();
        if (event != null
            && event.getOccurredOn() != null
            && messageId.equals(event.getEventId())) {
          meters
              .timer("shop.consumer.lag", "consumer", handlerId)
              .record(Duration.between(event.getOccurredOn(), clock.instant()).abs());
        }
      }

      @Override
      public void release(String handlerId, String messageId) {
        store.release(handlerId, messageId);
      }

      @Override
      public <T> T runInScope(Supplier<T> work) {
        return store.runInScope(work);
      }
    };
  }

  private void count(String consumer, String outcome) {
    meters.counter("shop.consumer.events", "consumer", consumer, "outcome", outcome).increment();
  }
}
