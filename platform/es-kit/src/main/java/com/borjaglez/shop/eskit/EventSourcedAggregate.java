package com.borjaglez.shop.eskit;

import java.util.ArrayList;
import java.util.List;

import com.borjaglez.cqrs.event.Event;

/**
 * Base class of event-sourced aggregates.
 *
 * <p>State changes only through events: command methods validate and call {@link #apply(Event)},
 * and {@link #when(Event)} mutates state. Loading replays the stored events through the same {@code
 * when}, so the state after a command and after a reload is identical by construction.
 */
public abstract class EventSourcedAggregate {

  private long version;
  private final List<Event> pendingChanges = new ArrayList<>();

  /** Identifier of the stream. */
  public abstract String id();

  /** Applies an event to the state. Must not validate or fail: the event already happened. */
  protected abstract void when(Event event);

  /** Records a new event and applies it. */
  protected final void apply(Event event) {
    when(event);
    pendingChanges.add(event);
  }

  /** Rebuilds the state from history. */
  public final void replay(List<? extends Event> history) {
    for (Event event : history) {
      when(event);
      version++;
    }
  }

  /** Number of committed events. */
  public final long version() {
    return version;
  }

  public final List<Event> pendingChanges() {
    return List.copyOf(pendingChanges);
  }

  /** Called once the pending changes are stored. */
  public final void markCommitted() {
    version += pendingChanges.size();
    pendingChanges.clear();
  }
}
