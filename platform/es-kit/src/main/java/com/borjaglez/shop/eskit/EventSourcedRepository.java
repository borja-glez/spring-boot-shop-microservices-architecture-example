package com.borjaglez.shop.eskit;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.borjaglez.shop.kernel.domain.DomainEvent;
import com.borjaglez.shop.kernel.domain.EventSourcedAggregate;

/**
 * Loads and saves one type of event-sourced aggregate whose events are domain events, through the
 * {@link DomainEventStore}. Saving needs the caller's transaction, as every write of the store.
 *
 * @param <A> the aggregate
 * @param <E> the type of its events
 */
public class EventSourcedRepository<A extends EventSourcedAggregate<E>, E extends DomainEvent> {

  private final DomainEventStore store;
  private final String streamType;
  private final Supplier<A> factory;

  public EventSourcedRepository(DomainEventStore store, String streamType, Supplier<A> factory) {
    this.store = store;
    this.streamType = streamType;
    this.factory = factory;
  }

  /** The aggregate rebuilt from its stream; empty if the stream has no events. */
  public Optional<A> load(String id) {
    List<StoredDomainEvent> history = store.load(streamType, id);
    if (history.isEmpty()) {
      return Optional.empty();
    }
    A aggregate = factory.get();
    aggregate.replay(events(history));
    return Optional.of(aggregate);
  }

  /**
   * Appends the pending changes, checking that nobody else wrote since the aggregate was loaded.
   *
   * @throws ConcurrencyConflictException if the aggregate is stale
   */
  public void save(A aggregate) {
    List<E> changes = aggregate.pendingChanges();
    if (changes.isEmpty()) {
      return;
    }
    store.append(streamType, aggregate.id(), aggregate.version(), changes);
    aggregate.markCommitted();
  }

  /** The stream holds only events of this aggregate, stored by {@link #save}. */
  @SuppressWarnings("unchecked")
  private List<E> events(List<StoredDomainEvent> history) {
    return history.stream().map(stored -> (E) stored.event()).toList();
  }
}
