package com.borjaglez.shop.eskit;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.borjaglez.cqrs.event.Event;

/** Loads and saves one type of event-sourced aggregate. */
public class AggregateStore<A extends EventSourcedAggregate> {

  private final EventStore eventStore;
  private final String streamType;
  private final Supplier<A> factory;

  public AggregateStore(EventStore eventStore, String streamType, Supplier<A> factory) {
    this.eventStore = eventStore;
    this.streamType = streamType;
    this.factory = factory;
  }

  public Optional<A> load(String id) {
    List<RecordedEvent> history = eventStore.load(streamType, id);
    if (history.isEmpty()) {
      return Optional.empty();
    }
    A aggregate = factory.get();
    aggregate.replay(history.stream().map(RecordedEvent::event).toList());
    return Optional.of(aggregate);
  }

  /**
   * Appends the pending changes, checking that nobody else wrote since the aggregate was loaded.
   *
   * @return the events that were stored
   * @throws ConcurrencyConflictException if the aggregate is stale
   */
  public List<Event> save(A aggregate) {
    List<Event> changes = aggregate.pendingChanges();
    if (changes.isEmpty()) {
      return changes;
    }
    eventStore.append(streamType, aggregate.id(), aggregate.version(), changes);
    aggregate.markCommitted();
    return changes;
  }
}
