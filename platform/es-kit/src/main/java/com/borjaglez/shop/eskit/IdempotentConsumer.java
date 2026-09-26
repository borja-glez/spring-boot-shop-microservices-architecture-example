package com.borjaglez.shop.eskit;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.specrepository.core.Operators;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Applies a message at most once per consumer.
 *
 * <p>The marker and the effect commit in the same transaction: if the effect fails, neither is
 * stored and a redelivery tries again; if it succeeds, redeliveries are skipped.
 *
 * <p>Metrics: {@code shop.consumer.events} counts the messages of each consumer by outcome ({@code
 * applied} or {@code duplicate}), and {@code shop.consumer.lag} times how long after it happened an
 * event reached the consumer's view: the eventual-consistency window of that read model.
 */
public class IdempotentConsumer {

  private final ProcessedMessageRepository processed;
  private final Clock clock;
  private final MeterRegistry meters;

  public IdempotentConsumer(
      ProcessedMessageRepository processed, Clock clock, MeterRegistry meters) {
    this.processed = processed;
    this.clock = clock;
    this.meters = meters;
  }

  /**
   * Applies {@code event} unless {@code consumer} already processed it, and records how long after
   * it happened it was applied.
   *
   * @return whether the effect ran
   */
  @Transactional
  public boolean once(String consumer, Event event, Runnable effect) {
    boolean ran = once(consumer, event.getEventId(), effect);
    if (ran && event.getOccurredOn() != null) {
      meters
          .timer("shop.consumer.lag", "consumer", consumer)
          .record(Duration.between(event.getOccurredOn(), clock.instant()).abs());
    }
    return ran;
  }

  /**
   * Runs {@code effect} unless {@code consumer} already processed {@code messageId}.
   *
   * @return whether the effect ran
   */
  @Transactional
  public boolean once(String consumer, String messageId, Runnable effect) {
    boolean seen =
        processed
                .query()
                .where("consumer", Operators.EQUALS, consumer)
                .where("messageId", Operators.EQUALS, messageId)
                .count()
            > 0;
    if (seen) {
      meters
          .counter("shop.consumer.events", "consumer", consumer, "outcome", "duplicate")
          .increment();
      return false;
    }
    processed.save(new ProcessedMessage(consumer, messageId, OffsetDateTime.now(clock)));
    effect.run();
    meters.counter("shop.consumer.events", "consumer", consumer, "outcome", "applied").increment();
    return true;
  }
}
