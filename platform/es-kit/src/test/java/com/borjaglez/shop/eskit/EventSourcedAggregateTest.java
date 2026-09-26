package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.eskit.fixtures.Counter;
import com.borjaglez.shop.eskit.fixtures.CounterIncremented;

class EventSourcedAggregateTest {

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

    counter.replay(List.of(new CounterIncremented("c-1", 4), new CounterIncremented("c-1", 1)));

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
}
