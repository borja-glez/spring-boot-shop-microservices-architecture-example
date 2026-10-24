package com.borjaglez.shop.eskit;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.borjaglez.cqrs.jdbc.outbox.OutboxEventBus;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.borjaglez.shop.kernel.domain.DomainEvent;
import com.borjaglez.shop.support.tracing.TraceCarrier;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.jpa.SpecificationExecutableQuery;

/**
 * Stores domain events and hands their integration events to the transactional outbox of
 * spring-boot-cqrs, both in the caller's transaction.
 *
 * <p>Each write inserts one {@code event_store} row per domain event, under its {@linkplain
 * DomainEventTypes name} and with the message context and trace of the request as metadata, and
 * publishes the event's {@linkplain IntegrationEventTranslator translations} through {@link
 * OutboxEventBus}, whose relay sends them on after commit. The events are stored, and other
 * services hear about them, if and only if the business transaction commits.
 *
 * <p>The rows are written as published ({@code published_at} is the write instant): their
 * integration events reach the outbox in that same write, so the event store's own {@link
 * OutboxRelay} never picks them up.
 *
 * <p>Writes join the transaction of whoever runs the use case; without one they fail with {@link
 * IllegalStateException} and store nothing.
 */
public class DomainEventStore {

  private final StoredEventRepository repository;
  private final DomainEventTypes types;
  private final IntegrationEventTranslator translator;
  private final OutboxEventBus outbox;
  private final MessageSerializer serializer;
  private final Clock clock;
  private final TraceCarrier traces;

  public DomainEventStore(
      StoredEventRepository repository,
      DomainEventTypes types,
      IntegrationEventTranslator translator,
      OutboxEventBus outbox,
      MessageSerializer serializer,
      Clock clock,
      TraceCarrier traces) {
    this.repository = repository;
    this.types = types;
    this.translator = translator;
    this.outbox = outbox;
    this.serializer = serializer;
    this.clock = clock;
    this.traces = traces;
  }

  /**
   * Appends events to an event-sourced stream.
   *
   * @param expectedVersion version the caller read; 0 for a new stream
   * @throws ConcurrencyConflictException if the stream moved on in the meantime
   * @throws IllegalStateException if no transaction is active
   */
  public void append(
      String streamType,
      String streamId,
      long expectedVersion,
      List<? extends DomainEvent> events) {
    requireTransaction(streamType, streamId);
    if (stream(streamType, streamId).count() != expectedVersion) {
      throw new ConcurrencyConflictException(streamType, streamId, expectedVersion);
    }
    try {
      repository.saveAllAndFlush(toRows(streamType, streamId, expectedVersion + 1, events));
    } catch (DataIntegrityViolationException raced) {
      // Another transaction appended the same versions between our check and our insert.
      throw new ConcurrencyConflictException(streamType, streamId, expectedVersion);
    }
    enqueueTranslations(events);
  }

  /**
   * Records the events of a state-based aggregate: no version, no concurrency check.
   *
   * @throws IllegalStateException if no transaction is active
   */
  public void record(String streamType, String streamId, List<? extends DomainEvent> events) {
    requireTransaction(streamType, streamId);
    repository.saveAll(toRows(streamType, streamId, null, events));
    enqueueTranslations(events);
  }

  /** Loads an event-sourced stream in version order. */
  public List<StoredDomainEvent> load(String streamType, String streamId) {
    return stream(streamType, streamId).sort(Sort.by("version")).findAll().stream()
        .map(this::toStored)
        .toList();
  }

  private static void requireTransaction(String streamType, String streamId) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "Storing the events of "
              + streamType
              + " "
              + streamId
              + " needs an active transaction, so that they commit with the changes that caused"
              + " them");
    }
  }

  /** The versioned rows of a stream: the events recorded for state-based aggregates have none. */
  private SpecificationExecutableQuery<StoredEvent> stream(String streamType, String streamId) {
    return repository
        .query()
        .where("streamType", Operators.EQUALS, streamType)
        .where("streamId", Operators.EQUALS, streamId)
        .where("version", Operators.IS_NOT_NULL, null);
  }

  /** One row per event, numbered from {@code firstVersion}, or unversioned when it is null. */
  private List<StoredEvent> toRows(
      String streamType, String streamId, Long firstVersion, List<? extends DomainEvent> events) {
    OffsetDateTime now = OffsetDateTime.now(clock);
    String metadata = json(EventMetadata.current(traces));
    List<StoredEvent> rows = new ArrayList<>();
    for (int i = 0; i < events.size(); i++) {
      DomainEvent event = events.get(i);
      StoredEvent row =
          new StoredEvent(
              UUID.randomUUID(),
              streamType,
              streamId,
              firstVersion == null ? null : firstVersion + i,
              types.nameOf(event.getClass()),
              json(event),
              metadata,
              now);
      // Its integration events reach the outbox in this same write.
      row.markPublished(now);
      rows.add(row);
    }
    return rows;
  }

  private void enqueueTranslations(List<? extends DomainEvent> events) {
    for (DomainEvent event : events) {
      outbox.publish(translator.translate(event));
    }
  }

  private String json(Object value) {
    return new String(serializer.serialize(value), StandardCharsets.UTF_8);
  }

  private StoredDomainEvent toStored(StoredEvent row) {
    DomainEvent event =
        serializer.deserialize(
            row.getPayload().getBytes(StandardCharsets.UTF_8), types.typeOf(row.getEventType()));
    return new StoredDomainEvent(row.getVersion(), event, row.getEventType(), row.getOccurredAt());
  }
}
