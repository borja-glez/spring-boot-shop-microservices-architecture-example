package com.borjaglez.shop.kernel.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class of state-based aggregates that collect the domain events produced by their commands.
 *
 * @param <E> the type of events the aggregate records
 */
public abstract class AggregateRoot<E extends DomainEvent> {

  private final List<E> events = new ArrayList<>();

  /** Records an event produced by a state change. */
  protected final void record(E event) {
    events.add(event);
  }

  /** Returns the recorded events and clears them. */
  public final List<E> pullEvents() {
    List<E> pulled = List.copyOf(events);
    events.clear();
    return pulled;
  }
}
