package com.borjaglez.shop.eskit.fixtures;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.shop.eskit.EventSourcedAggregate;

/** Smallest possible event-sourced aggregate, used by the es-kit tests. */
public class Counter extends EventSourcedAggregate {

  private String id;
  private int total;

  public static Counter start(String id) {
    Counter counter = new Counter();
    counter.id = id;
    return counter;
  }

  public void increment(int amount) {
    apply(new CounterIncremented(id, amount));
  }

  @Override
  public String id() {
    return id;
  }

  public int total() {
    return total;
  }

  @Override
  protected void when(Event event) {
    if (event instanceof CounterIncremented incremented) {
      id = incremented.getCounterId();
      total += incremented.getAmount();
    }
  }
}
