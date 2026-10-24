package com.borjaglez.shop.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class EventSourcedAggregateTest {

  record Incremented(String counterId, int amount, Instant occurredOn) implements DomainEvent {}

  static class Counter extends EventSourcedAggregate<Incremented> {

    private String id;
    private int total;

    static Counter start(String id) {
      Counter counter = new Counter();
      counter.id = id;
      return counter;
    }

    void increment(int amount) {
      apply(new Incremented(id, amount, Instant.EPOCH));
    }

    @Override
    public String id() {
      return id;
    }

    int total() {
      return total;
    }

    @Override
    protected void when(Incremented event) {
      id = event.counterId();
      total += event.amount();
    }
  }

  @Test
  void newChangesUpdateStateAndStayPending() {
    Counter counter = Counter.start("c-1");

    counter.increment(2);
    counter.increment(3);

    assertThat(counter.total()).isEqualTo(5);
    assertThat(counter.version()).isZero();
    assertThat(counter.pendingChanges()).hasSize(2);
  }

  @Test
  void replayRebuildsStateAndVersionWithoutPendingChanges() {
    Counter counter = new Counter();

    counter.replay(
        List.of(
            new Incremented("c-1", 4, Instant.EPOCH), new Incremented("c-1", 1, Instant.EPOCH)));

    assertThat(counter.id()).isEqualTo("c-1");
    assertThat(counter.total()).isEqualTo(5);
    assertThat(counter.version()).isEqualTo(2);
    assertThat(counter.pendingChanges()).isEmpty();
  }

  @Test
  void markingChangesCommittedAdvancesTheVersion() {
    Counter counter = Counter.start("c-1");
    counter.increment(1);

    counter.markCommitted();

    assertThat(counter.version()).isEqualTo(1);
    assertThat(counter.pendingChanges()).isEmpty();
  }

  @Test
  void pendingChangesAreACopy() {
    Counter counter = Counter.start("c-1");
    counter.increment(1);

    List<Incremented> pending = counter.pendingChanges();
    counter.markCommitted();

    assertThat(pending).hasSize(1);
    assertThat(counter.pendingChanges()).isEmpty();
  }
}
