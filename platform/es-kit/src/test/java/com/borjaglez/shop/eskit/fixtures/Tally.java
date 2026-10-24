package com.borjaglez.shop.eskit.fixtures;

import java.time.Instant;

import com.borjaglez.shop.kernel.domain.EventSourcedAggregate;

/** Smallest possible event-sourced aggregate with domain events, used by the es-kit tests. */
public class Tally extends EventSourcedAggregate<TallyEvent> {

  private String id;
  private int total;

  public static Tally open(String id) {
    Tally tally = new Tally();
    tally.apply(new TallyEvent.Opened(id, Instant.now()));
    return tally;
  }

  public void increment(int amount) {
    apply(new TallyEvent.Incremented(id, amount, Instant.now()));
  }

  @Override
  public String id() {
    return id;
  }

  public int total() {
    return total;
  }

  @Override
  protected void when(TallyEvent event) {
    switch (event) {
      case TallyEvent.Opened opened -> id = opened.tallyId();
      case TallyEvent.Incremented incremented -> total += incremented.amount();
    }
  }
}
