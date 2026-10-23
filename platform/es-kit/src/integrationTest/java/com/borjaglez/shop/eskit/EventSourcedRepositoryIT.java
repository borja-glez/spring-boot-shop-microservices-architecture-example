package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.shop.eskit.fixtures.Tally;
import com.borjaglez.shop.eskit.fixtures.TallyEvent;

@DomainEventsIntegrationTest
class EventSourcedRepositoryIT {

  @Autowired DomainEventStore store;
  @Autowired PlatformTransactionManager transactionManager;

  private EventSourcedRepository<Tally, TallyEvent> tallies() {
    return new EventSourcedRepository<>(store, "tally", Tally::new);
  }

  private void inTransaction(Runnable work) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
  }

  @Test
  void savesAndReloads() {
    String id = UUID.randomUUID().toString();
    Tally tally = Tally.open(id);
    tally.increment(3);
    tally.increment(4);

    inTransaction(() -> tallies().save(tally));
    Tally loaded = tallies().load(id).orElseThrow();

    assertThat(loaded.id()).isEqualTo(id);
    assertThat(loaded.total()).isEqualTo(7);
    assertThat(loaded.version()).isEqualTo(3);
    assertThat(tally.version()).isEqualTo(3);
    assertThat(tally.pendingChanges()).isEmpty();

    loaded.increment(1);
    inTransaction(() -> tallies().save(loaded));

    assertThat(tallies().load(id).orElseThrow())
        .satisfies(
            again -> {
              assertThat(again.total()).isEqualTo(8);
              assertThat(again.version()).isEqualTo(4);
            });
    assertThat(tallies().load(UUID.randomUUID().toString())).isEmpty();
  }

  @Test
  void saveWithoutChangesDoesNothing() {
    String id = UUID.randomUUID().toString();
    Tally tally = Tally.open(id);
    inTransaction(() -> tallies().save(tally));
    Tally loaded = tallies().load(id).orElseThrow();

    // Nothing to write, so not even a transaction is needed.
    tallies().save(loaded);

    assertThat(loaded.version()).isEqualTo(1);
    assertThat(store.load("tally", id)).hasSize(1);
  }
}
