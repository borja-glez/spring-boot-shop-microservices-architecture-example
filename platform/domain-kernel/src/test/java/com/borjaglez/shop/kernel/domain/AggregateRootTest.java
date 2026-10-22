package com.borjaglez.shop.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class AggregateRootTest {

  record Happened(String what, Instant occurredOn) implements DomainEvent {}

  static class Sample extends AggregateRoot<Happened> {
    void happen(String what) {
      record(new Happened(what, Instant.EPOCH));
    }
  }

  @Test
  void recordedEventsArePulledInOrderAndCleared() {
    Sample sample = new Sample();
    sample.happen("a");
    sample.happen("b");

    List<Happened> pulled = sample.pullEvents();

    assertThat(pulled).extracting(Happened::what).containsExactly("a", "b");
    assertThat(sample.pullEvents()).isEmpty();
  }

  @Test
  void pulledListIsACopy() {
    Sample sample = new Sample();
    sample.happen("a");

    List<Happened> pulled = sample.pullEvents();

    assertThatThrownBy(() -> pulled.add(new Happened("x", Instant.EPOCH)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void aNewAggregateHasNoEvents() {
    assertThat(new Sample().pullEvents()).isEmpty();
  }
}
