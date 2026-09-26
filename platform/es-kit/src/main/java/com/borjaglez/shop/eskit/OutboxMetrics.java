package com.borjaglez.shop.eskit;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.borjaglez.specrepository.core.Operators;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * The outbox backlog, the first thing to look at when events seem late:
 *
 * <ul>
 *   <li>{@code shop.outbox.pending}: stored events the relay has not published yet;
 *   <li>{@code shop.outbox.oldest.age.seconds}: how long the oldest of them has been waiting. It
 *       grows while Kafka or the relay is down and drops to zero once the backlog is published.
 * </ul>
 *
 * Both are read from the event store when the metrics are collected (pending rows have their own
 * partial index); a failed read reports {@code NaN} instead of breaking the collection.
 */
public class OutboxMetrics implements MeterBinder {

  private static final Logger log = LoggerFactory.getLogger(OutboxMetrics.class);

  private final StoredEventRepository events;
  private final Clock clock;

  public OutboxMetrics(StoredEventRepository events, Clock clock) {
    this.events = events;
    this.clock = clock;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder("shop.outbox.pending", this, OutboxMetrics::pending)
        .description("Events stored but not yet published")
        .register(registry);
    Gauge.builder("shop.outbox.oldest.age.seconds", this, OutboxMetrics::oldestAgeSeconds)
        .description("Seconds the oldest unpublished event has been waiting")
        .register(registry);
  }

  double pending() {
    try {
      return events.query().where("publishedAt", Operators.IS_NULL, null).count();
    } catch (RuntimeException e) {
      log.debug("Could not count pending events: {}", e.toString());
      return Double.NaN;
    }
  }

  double oldestAgeSeconds() {
    try {
      return events
          .query()
          .where("publishedAt", Operators.IS_NULL, null)
          .sort(Sort.by("globalPosition"))
          .findSlice(PageRequest.of(0, 1))
          .stream()
          .findFirst()
          .map(e -> ageInSeconds(e.getOccurredAt()))
          .orElse(0.0);
    } catch (RuntimeException e) {
      log.debug("Could not read the oldest pending event: {}", e.toString());
      return Double.NaN;
    }
  }

  private double ageInSeconds(OffsetDateTime since) {
    return Math.max(Duration.between(since, OffsetDateTime.now(clock)).toMillis(), 0) / 1000.0;
  }
}
