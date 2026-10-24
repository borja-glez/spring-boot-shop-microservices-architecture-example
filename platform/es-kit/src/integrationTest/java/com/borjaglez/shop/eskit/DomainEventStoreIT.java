package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.borjaglez.shop.eskit.EsKitTestApplication.RecordingDestination;
import com.borjaglez.shop.eskit.EsKitTestApplication.RecordingTraceCarrier;
import com.borjaglez.shop.eskit.fixtures.CounterIncremented;
import com.borjaglez.shop.eskit.fixtures.TallyEvent;
import com.borjaglez.shop.support.tracing.TraceCarrier;
import com.borjaglez.specrepository.core.Operators;

@DomainEventsIntegrationTest
class DomainEventStoreIT {

  @Autowired DomainEventStore store;
  @Autowired StoredEventRepository storedEvents;
  @Autowired JdbcTemplate jdbc;
  @Autowired MessageSerializer serializer;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired OutboxRelay esKitRelay;
  @Autowired RecordingDestination destination;
  @Autowired OutboxMetrics backlog;

  private static String newTally() {
    return UUID.randomUUID().toString();
  }

  private static TallyEvent.Opened opened(String tally) {
    return new TallyEvent.Opened(tally, Instant.now());
  }

  private static TallyEvent.Incremented incremented(String tally, int amount) {
    return new TallyEvent.Incremented(tally, amount, Instant.now());
  }

  private void inTransaction(Runnable work) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
  }

  private List<StoredEvent> rows(String tally) {
    return storedEvents
        .query()
        .where("streamId", Operators.EQUALS, tally)
        .sort(Sort.by("globalPosition"))
        .findAll();
  }

  /** The outbox rows of the integration events translated from a tally's domain events. */
  private List<Map<String, Object>> outboxRows(String tally) {
    return jdbc.queryForList(
        "select event_id, event_name, event_class, published_at,"
            + " convert_from(payload, 'UTF8')::jsonb ->> 'amount' as amount,"
            + " convert_from(context, 'UTF8') as context"
            + " from cqrs_outbox"
            + " where convert_from(payload, 'UTF8')::jsonb ->> 'counterId' = ? order by id",
        tally);
  }

  @Test
  void recordStoresDomainEventsAndEnqueuesTheirTranslations() {
    String tally = newTally();

    inTransaction(
        () -> store.record("tally", tally, List.of(opened(tally), incremented(tally, 3))));

    assertThat(rows(tally))
        .extracting(StoredEvent::getEventType)
        .containsExactly("tally-opened", "tally-incremented");
    assertThat(rows(tally))
        .allSatisfy(
            row -> {
              assertThat(row.getStreamType()).isEqualTo("tally");
              assertThat(row.getVersion()).isNull();
              // Its integration events went to the outbox in the same write.
              assertThat(row.getPublishedAt()).isEqualTo(row.getOccurredAt());
            });
    assertThat(outboxRows(tally))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row)
                  .containsEntry("event_name", DomainEventsTestConfiguration.COUNTER_INCREMENTED)
                  .containsEntry("event_class", CounterIncremented.class.getName())
                  .containsEntry("amount", "3");
              assertThat(row.get("published_at")).isNull();
            });
  }

  @Test
  void appendDetectsConcurrentWriters() throws Exception {
    String tally = newTally();
    inTransaction(() -> store.append("tally", tally, 0, List.of(opened(tally))));

    assertThatThrownBy(
            () ->
                inTransaction(
                    () -> store.append("tally", tally, 0, List.of(incremented(tally, 1)))))
        .isInstanceOf(ConcurrencyConflictException.class)
        .hasFieldOrPropertyWithValue("code", "concurrent-modification");

    // Both read version 1; the second writer waits for the first one's version 2 and loses.
    CountDownLatch firstWrote = new CountDownLatch(1);
    CountDownLatch firstMayCommit = new CountDownLatch(1);
    ExecutorService writers = Executors.newFixedThreadPool(2);
    try {
      Future<?> first =
          writers.submit(
              () ->
                  inTransaction(
                      () -> {
                        store.append("tally", tally, 1, List.of(incremented(tally, 2)));
                        firstWrote.countDown();
                        awaitQuietly(firstMayCommit);
                      }));
      assertThat(firstWrote.await(30, TimeUnit.SECONDS)).isTrue();
      Future<?> second =
          writers.submit(
              () ->
                  inTransaction(
                      () -> store.append("tally", tally, 1, List.of(incremented(tally, 5)))));
      await().atMost(Duration.ofSeconds(30)).until(this::aWriterWaitsForALock);
      firstMayCommit.countDown();

      first.get(30, TimeUnit.SECONDS);
      assertThatThrownBy(() -> second.get(30, TimeUnit.SECONDS))
          .hasCauseInstanceOf(ConcurrencyConflictException.class);
    } finally {
      writers.shutdownNow();
    }

    assertThat(store.load("tally", tally))
        .extracting(StoredDomainEvent::version)
        .containsExactly(1L, 2L);
    assertThat(outboxRows(tally)).extracting(row -> row.get("amount")).containsExactly("2");
  }

  private boolean aWriterWaitsForALock() {
    return jdbc.queryForObject(
            "select count(*) from pg_stat_activity"
                + " where datname = current_database() and wait_event_type = 'Lock'",
            Integer.class)
        > 0;
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void loadReturnsTheStreamInOrder() {
    String tally = newTally();
    TallyEvent.Opened opened = opened(tally);
    TallyEvent.Incremented first = incremented(tally, 1);
    TallyEvent.Incremented second = incremented(tally, 4);
    inTransaction(() -> store.append("tally", tally, 0, List.of(opened, first)));
    inTransaction(() -> store.append("tally", tally, 2, List.of(second)));

    List<StoredDomainEvent> loaded = store.load("tally", tally);

    assertThat(loaded).extracting(StoredDomainEvent::version).containsExactly(1L, 2L, 3L);
    assertThat(loaded).extracting(StoredDomainEvent::event).containsExactly(opened, first, second);
    assertThat(loaded)
        .extracting(StoredDomainEvent::eventType)
        .containsExactly("tally-opened", "tally-incremented", "tally-incremented");
    assertThat(loaded).allSatisfy(event -> assertThat(event.occurredAt()).isNotNull());
    assertThat(store.load("tally", newTally())).isEmpty();
  }

  @Test
  void writesRequireATransaction() {
    String tally = newTally();

    assertThatThrownBy(() -> store.append("tally", tally, 0, List.of(opened(tally))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transaction");
    assertThatThrownBy(() -> store.record("tally", tally, List.of(incremented(tally, 1))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transaction");
    assertThat(rows(tally)).isEmpty();
    assertThat(outboxRows(tally)).isEmpty();
  }

  @Test
  void rollbackDiscardsEventsAndOutboxRows() {
    String tally = newTally();

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              store.append("tally", tally, 0, List.of(opened(tally), incremented(tally, 2)));
              store.record("tally-log", tally, List.of(incremented(tally, 3)));
              status.setRollbackOnly();
            });

    assertThat(rows(tally)).isEmpty();
    assertThat(outboxRows(tally)).isEmpty();
  }

  @Test
  void metadataCarriesCorrelationAndTrace() throws Exception {
    String tally = newTally();
    try (var scope =
        MessageContext.scope(
            MessageContext.empty().with(MessageContext.CORRELATION_ID_KEY, "tally-corr-1"))) {
      inTransaction(() -> store.record("tally", tally, List.of(incremented(tally, 1))));
    }

    @SuppressWarnings("unchecked")
    Map<String, String> metadata =
        serializer.deserialize(
            rows(tally).getFirst().getMetadata().getBytes(StandardCharsets.UTF_8), Map.class);
    assertThat(metadata)
        .containsEntry(MessageContext.CORRELATION_ID_KEY, "tally-corr-1")
        .containsEntry(TraceCarrier.TRACEPARENT, RecordingTraceCarrier.TRACE);
    // The integration event leaves with the same context.
    assertThat(outboxRows(tally))
        .singleElement()
        .satisfies(row -> assertThat((String) row.get("context")).contains("tally-corr-1"));
  }

  @Test
  void domainEventRowsAreNotPickedByTheEsKitRelay() {
    String tally = newTally();
    inTransaction(
        () -> store.record("tally", tally, List.of(opened(tally), incremented(tally, 1))));
    destination.reset();

    esKitRelay.relayPending();

    assertThat(destination.published).isEmpty();
    assertThat(rows(tally))
        .hasSize(2)
        .allSatisfy(
            row -> {
              assertThat(row.getPublishAttempts()).isZero();
              assertThat(row.getLastError()).isNull();
            });
    assertThat(backlog.pending()).isZero();
  }
}
