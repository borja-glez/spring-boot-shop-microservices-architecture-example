package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.shop.eskit.fixtures.CounterIncremented;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.specrepository.core.Operators;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class EventStoreIT {

  @Autowired EventStore store;
  @Autowired StoredEventRepository storedEvents;

  private static String newStream() {
    return UUID.randomUUID().toString();
  }

  @Test
  void appendedEventsLoadBackInOrderWithTheirIdentity() {
    String stream = newStream();
    CounterIncremented first = new CounterIncremented(stream, 4);
    CounterIncremented second = new CounterIncremented(stream, 1);

    store.append("counter", stream, 0, List.of(first, second));

    List<RecordedEvent> loaded = store.load("counter", stream);
    assertThat(loaded).extracting(RecordedEvent::version).containsExactly(1L, 2L);
    assertThat(loaded)
        .extracting(r -> r.event().getEventId())
        .containsExactly(first.getEventId(), second.getEventId());
    assertThat(((CounterIncremented) loaded.get(1).event()).getAmount()).isEqualTo(1);
  }

  @Test
  void unknownStreamsAreEmpty() {
    assertThat(store.load("counter", newStream())).isEmpty();
  }

  @Test
  void metadataKeepsTheCorrelationId() throws Exception {
    String stream = newStream();
    try (var scope =
        MessageContext.scope(
            MessageContext.empty().with(MessageContext.CORRELATION_ID_KEY, "corr-42"))) {
      store.append("counter", stream, 0, List.of(new CounterIncremented(stream, 1)));
    }

    StoredEvent stored =
        storedEvents.query().where("streamId", Operators.EQUALS, stream).findOne().orElseThrow();
    assertThat(stored.getMetadata()).contains("corr-42");
    assertThat(stored.getEventType())
        .isEqualTo("shop.eskit-test.1.event.counter.counter-incremented");
    assertThat(stored.getPublishedAt()).isNull();
  }

  @Test
  void appendingAgainstAStaleVersionIsAConflict() {
    String stream = newStream();
    store.append("counter", stream, 0, List.of(new CounterIncremented(stream, 1)));

    assertThatThrownBy(
            () -> store.append("counter", stream, 0, List.of(new CounterIncremented(stream, 1))))
        .isInstanceOf(ConcurrencyConflictException.class)
        .hasFieldOrPropertyWithValue("code", "concurrent-modification");
  }

  @Test
  void onlyOneOfTwoConcurrentWritersWins() throws Exception {
    String stream = newStream();
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Boolean>> results =
          List.of(pool.submit(() -> write(stream, start)), pool.submit(() -> write(stream, start)));
      start.countDown();
      long winners = results.stream().filter(EventStoreIT::succeeded).count();
      assertThat(winners).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
    assertThat(store.load("counter", stream)).hasSize(1);
  }

  private Boolean write(String stream, CountDownLatch start) throws InterruptedException {
    start.await();
    try {
      store.append("counter", stream, 0, List.of(new CounterIncremented(stream, 1)));
      return true;
    } catch (ConcurrencyConflictException e) {
      return false;
    }
  }

  private static boolean succeeded(Future<Boolean> result) {
    try {
      return result.get();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void outboxMessagesAreRecordedWithoutVersion() {
    String stream = newStream();

    store.record("product", stream, List.of(new CounterIncremented(stream, 1)));
    store.record("product", stream, List.of(new CounterIncremented(stream, 2)));

    List<StoredEvent> rows =
        storedEvents.query().where("streamId", Operators.EQUALS, stream).findAll();
    assertThat(rows).hasSize(2).allMatch(r -> r.getVersion() == null);
  }
}
