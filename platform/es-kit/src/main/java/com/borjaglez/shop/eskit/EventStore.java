package com.borjaglez.shop.eskit;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.borjaglez.shop.support.tracing.TraceCarrier;
import com.borjaglez.specrepository.core.Operators;

/**
 * Append-only event store that is also the transactional outbox.
 *
 * <p>Writing a stream and "sending" its events is the same insert, so events are published if and
 * only if the business transaction commits. {@link OutboxRelay} publishes pending rows afterwards.
 */
public class EventStore {

  private final StoredEventRepository repository;
  private final EventTypeRegistry types;
  private final MessageSerializer serializer;
  private final Clock clock;
  private final TraceCarrier traces;

  public EventStore(
      StoredEventRepository repository,
      EventTypeRegistry types,
      MessageSerializer serializer,
      Clock clock,
      TraceCarrier traces) {
    this.repository = repository;
    this.types = types;
    this.serializer = serializer;
    this.clock = clock;
    this.traces = traces;
  }

  /**
   * Appends events to an event-sourced stream.
   *
   * @param expectedVersion version the caller read; 0 for a new stream
   * @throws ConcurrencyConflictException if the stream moved on in the meantime
   */
  @Transactional
  public void append(
      String streamType, String streamId, long expectedVersion, List<? extends Event> events) {
    long current = currentVersion(streamType, streamId);
    if (current != expectedVersion) {
      throw new ConcurrencyConflictException(streamType, streamId, expectedVersion);
    }
    List<StoredEvent> rows = new ArrayList<>();
    long version = expectedVersion;
    for (Event event : events) {
      rows.add(toRow(streamType, streamId, ++version, event));
    }
    try {
      repository.saveAllAndFlush(rows);
    } catch (DataIntegrityViolationException raced) {
      // Another transaction appended the same versions between our check and our insert.
      throw new ConcurrencyConflictException(streamType, streamId, expectedVersion);
    }
  }

  /** Records outbox messages of a state-based aggregate: no version, no concurrency check. */
  @Transactional
  public void record(String streamType, String streamId, List<? extends Event> events) {
    repository.saveAll(events.stream().map(e -> toRow(streamType, streamId, null, e)).toList());
  }

  /** Loads a stream in version order. */
  @Transactional(readOnly = true)
  public List<RecordedEvent> load(String streamType, String streamId) {
    return repository
        .query()
        .where("streamType", Operators.EQUALS, streamType)
        .where("streamId", Operators.EQUALS, streamId)
        .where("version", Operators.IS_NOT_NULL, null)
        .sort(Sort.by("version"))
        .findAll()
        .stream()
        .map(this::toRecorded)
        .toList();
  }

  /** Deserializes a stored row. */
  public Event deserialize(StoredEvent row) {
    return serializer.deserialize(
        row.getPayload().getBytes(StandardCharsets.UTF_8), types.typeOf(row.getEventType()));
  }

  /** Reads the metadata of a stored row. */
  @SuppressWarnings("unchecked")
  public Map<String, String> metadata(StoredEvent row) {
    return serializer.deserialize(row.getMetadata().getBytes(StandardCharsets.UTF_8), Map.class);
  }

  private long currentVersion(String streamType, String streamId) {
    return repository
        .query()
        .where("streamType", Operators.EQUALS, streamType)
        .where("streamId", Operators.EQUALS, streamId)
        .where("version", Operators.IS_NOT_NULL, null)
        .count();
  }

  private StoredEvent toRow(String streamType, String streamId, Long version, Event event) {
    return new StoredEvent(
        UUID.fromString(event.getEventId()),
        streamType,
        streamId,
        version,
        types.nameOf(event.getClass()),
        new String(serializer.serialize(event), StandardCharsets.UTF_8),
        new String(serializer.serialize(currentMetadata()), StandardCharsets.UTF_8),
        OffsetDateTime.now(clock));
  }

  /**
   * The message context of the request plus the current trace, so the relay can publish the event
   * as part of the trace that recorded it.
   */
  private Map<String, String> currentMetadata() {
    Map<String, String> metadata = new HashMap<>(MessageContext.current().asMap());
    metadata.putAll(traces.capture());
    return metadata;
  }

  private RecordedEvent toRecorded(StoredEvent row) {
    return new RecordedEvent(
        row.getVersion(),
        deserialize(row),
        row.getEventType(),
        row.getOccurredAt(),
        row.getPublishedAt());
  }
}
