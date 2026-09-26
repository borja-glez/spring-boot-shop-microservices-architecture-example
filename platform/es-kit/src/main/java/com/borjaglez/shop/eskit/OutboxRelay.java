package com.borjaglez.shop.eskit;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.shop.support.chaos.Chaos;
import com.borjaglez.shop.support.chaos.ChaosFault;
import com.borjaglez.shop.support.tracing.TraceCarrier;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Publishes pending events in global order.
 *
 * <p>Each batch locks its rows with {@code FOR UPDATE SKIP LOCKED}, so several instances of a
 * service can relay in parallel without publishing the same row twice. On the first failure the
 * batch stops and the failed row is retried on the next run, so with a single relaying instance
 * later events never overtake an earlier one. With several instances another one may publish the
 * next batch meanwhile, so batches may interleave; consumers are designed not to rely on the order
 * across event types.
 *
 * <p>A row that can never be published (for example an event type that no longer exists) blocks the
 * rows behind it; {@code publish_attempts} and {@code last_error} show it in the event store
 * explorer. Delivery is at-least-once (a crash after publishing but before commit republishes),
 * which is why every consumer must be idempotent.
 *
 * <p>For the demo it registers two chaos faults: {@code relay.paused} (the relay stops, as if Kafka
 * were down: events pile up in the store and go out in order once it resumes) and {@code
 * relay.duplicate} (every event is published twice, which the idempotent consumers must absorb).
 *
 * <p>Metrics, recorded once a batch commits: {@code shop.outbox.published} and {@code
 * shop.outbox.failures} count publications by event type, and {@code shop.outbox.delay} times how
 * long each event waited between its business transaction and the broker's acknowledgement. {@link
 * OutboxMetrics} reports the backlog.
 *
 * <p>Row locking ({@code FOR UPDATE SKIP LOCKED}) is expressed as a native query.
 */
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  static final String PENDING =
      "select * from event_store where published_at is null "
          + "order by global_position limit :batch for update skip locked";

  private final EntityManager entityManager;
  private final TransactionTemplate transactions;
  private final EventStore eventStore;
  private final OutboxDestination destination;
  private final Clock clock;
  private final int batchSize;
  private final ChaosFault paused;
  private final ChaosFault duplicate;
  private final TraceCarrier traces;
  private final MeterRegistry meters;

  public OutboxRelay(
      EntityManager entityManager,
      TransactionTemplate transactions,
      EventStore eventStore,
      OutboxDestination destination,
      Clock clock,
      int batchSize,
      Chaos chaos,
      TraceCarrier traces,
      MeterRegistry meters) {
    this.entityManager = entityManager;
    this.transactions = transactions;
    this.eventStore = eventStore;
    this.destination = destination;
    this.clock = clock;
    this.batchSize = batchSize;
    this.traces = traces;
    this.meters = meters;
    this.paused =
        chaos.toggle(
            "relay.paused", "Stops publishing events to Kafka, as if the broker were down");
    this.duplicate =
        chaos.toggle("relay.duplicate", "Publishes every event twice (at-least-once delivery)");
  }

  /**
   * Publishes pending events until none is left or one fails.
   *
   * @return how many events were published
   */
  public int relayPending() {
    if (paused.active()) {
      return 0;
    }
    int total = 0;
    while (true) {
      Batch batch = transactions.execute(status -> relayBatch());
      record(batch);
      total += batch.published().size();
      if (batch.failed() != null || batch.published().size() < batchSize) {
        return total;
      }
    }
  }

  private Batch relayBatch() {
    @SuppressWarnings("unchecked")
    List<StoredEvent> pending =
        entityManager
            .createNativeQuery(PENDING, StoredEvent.class)
            .setParameter("batch", batchSize)
            .getResultList();
    List<StoredEvent> published = new ArrayList<>();
    for (StoredEvent row : pending) {
      try {
        publish(row);
        row.markPublished(OffsetDateTime.now(clock));
        published.add(row);
      } catch (RuntimeException e) {
        row.markFailed(e.toString());
        log.warn(
            "Could not publish event {} ({}); will retry: {}",
            row.getEventId(),
            row.getEventType(),
            e.toString());
        return new Batch(published, row);
      }
    }
    return new Batch(published, null);
  }

  private void record(Batch batch) {
    for (StoredEvent row : batch.published()) {
      meters.counter("shop.outbox.published", "event.type", row.getEventType()).increment();
      meters
          .timer("shop.outbox.delay")
          .record(Duration.between(row.getOccurredAt(), row.getPublishedAt()).abs());
    }
    if (batch.failed() != null) {
      meters
          .counter("shop.outbox.failures", "event.type", batch.failed().getEventType())
          .increment();
    }
  }

  private void publish(StoredEvent row) {
    Event event = eventStore.deserialize(row);
    Map<String, String> metadata = eventStore.metadata(row);
    Map<String, String> context = new HashMap<>(metadata);
    context.keySet().removeIf(TraceCarrier::isTraceHeader);
    traces.resume(
        metadata,
        "outbox publish " + row.getEventType(),
        () -> {
          try (var scope = MessageContext.scope(MessageContext.of(context))) {
            destination.publish(event);
            if (duplicate.active()) {
              destination.publish(event);
            }
          }
          return null;
        });
  }

  /** The rows a batch published, and the one it stopped at ({@code null} if none failed). */
  private record Batch(List<StoredEvent> published, StoredEvent failed) {}
}
