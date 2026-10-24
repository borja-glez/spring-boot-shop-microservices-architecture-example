package com.borjaglez.shop.kernel.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class of event-sourced aggregates.
 *
 * <p>State changes only through events: command methods validate and call {@link #apply}, and
 * {@link #when} mutates state. Loading replays the stored events through the same {@code when}, so
 * the state after a command and after a reload is identical by construction.
 *
 * @param <E> the type of events of the aggregate
 */
public abstract class EventSourcedAggregate<E extends DomainEvent> {

  private long version;
  private final List<E> pendingChanges = new ArrayList<>();

  /** Identifier of the stream. */
  public abstract String id();

  /** Applies an event to the state. Must not validate or fail: the event already happened. */
  protected abstract void when(E event);

  /** Records a new event and applies it. */
  protected final void apply(E event) {
    when(event);
    pendingChanges.add(event);
  }

  /** Rebuilds the state from history. */
  public final void replay(List<? extends E> history) {
    for (E event : history) {
      when(event);
      version++;
    }
  }

  /** Number of committed events. */
  public final long version() {
    return version;
  }

  /** Events applied since the last commit. */
  public final List<E> pendingChanges() {
    return List.copyOf(pendingChanges);
  }

  /** Called once the pending changes are stored. */
  public final void markCommitted() {
    version += pendingChanges.size();
    pendingChanges.clear();
  }
}
