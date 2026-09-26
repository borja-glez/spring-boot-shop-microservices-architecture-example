package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.shop.eskit.fixtures.Counter;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class AggregateStoreIT {

  @Autowired EventStore eventStore;

  private AggregateStore<Counter> counters() {
    return new AggregateStore<>(eventStore, "counter", Counter::new);
  }

  @Test
  void savedAggregatesLoadBackWithStateAndVersion() {
    String id = UUID.randomUUID().toString();
    Counter counter = Counter.start(id);
    counter.increment(3);
    counter.increment(4);

    counters().save(counter);
    Counter loaded = counters().load(id).orElseThrow();

    assertThat(loaded.total()).isEqualTo(7);
    assertThat(loaded.version()).isEqualTo(2);
    assertThat(counter.version()).isEqualTo(2);
    assertThat(counter.pendingChanges()).isEmpty();
  }

  @Test
  void unknownAggregatesAreEmpty() {
    assertThat(counters().load(UUID.randomUUID().toString())).isEmpty();
  }

  @Test
  void savingAStaleCopyIsAConflict() {
    String id = UUID.randomUUID().toString();
    Counter counter = Counter.start(id);
    counter.increment(1);
    counters().save(counter);
    Counter first = counters().load(id).orElseThrow();
    Counter second = counters().load(id).orElseThrow();
    first.increment(1);
    second.increment(1);
    counters().save(first);

    assertThatThrownBy(() -> counters().save(second))
        .isInstanceOf(ConcurrencyConflictException.class);
  }

  @Test
  void savingWithoutChangesDoesNothing() {
    String id = UUID.randomUUID().toString();

    counters().save(Counter.start(id));

    assertThat(counters().load(id)).isEmpty();
  }
}
