package com.borjaglez.shop.eskit.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.idempotency.Idempotent;

/** A read model consumer whose increments can be told to fail. */
@EventHandler
public class CounterProjector {

  public static final String CONSUMER = "eskit-test.counters";

  public final List<String> applied = new CopyOnWriteArrayList<>();
  public volatile boolean failing;

  @HandleEvent
  @Idempotent(name = CONSUMER)
  public void on(CounterIncremented event) {
    if (failing) {
      throw new IllegalStateException("projection failed");
    }
    applied.add(event.getEventId());
  }
}
