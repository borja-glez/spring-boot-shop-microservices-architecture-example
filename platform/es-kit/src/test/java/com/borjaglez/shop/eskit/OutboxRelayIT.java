package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.shop.eskit.EsKitTestApplication.RecordingDestination;
import com.borjaglez.shop.eskit.EsKitTestApplication.RecordingTraceCarrier;
import com.borjaglez.shop.eskit.fixtures.CounterIncremented;
import com.borjaglez.shop.support.chaos.Chaos;
import com.borjaglez.shop.support.tracing.TraceCarrier;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.specrepository.core.Operators;

import io.micrometer.core.instrument.MeterRegistry;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class OutboxRelayIT {

  @Autowired EventStore store;
  @Autowired OutboxRelay relay;
  @Autowired RecordingDestination destination;
  @Autowired StoredEventRepository storedEvents;
  @Autowired Chaos chaos;
  @Autowired RecordingTraceCarrier traces;
  @Autowired MeterRegistry meters;
  @Autowired OutboxMetrics backlog;

  @BeforeEach
  void drainAndReset() {
    chaos.reset();
    relay.relayPending();
    destination.reset();
    traces.resumed.clear();
  }

  @Test
  void publishesPendingEventsInTheOrderTheyWereWritten() {
    String stream = UUID.randomUUID().toString();
    CounterIncremented a = new CounterIncremented(stream, 1);
    CounterIncremented b = new CounterIncremented(stream, 2);
    CounterIncremented c = new CounterIncremented(stream, 3);
    store.append("counter", stream, 0, List.of(a, b));
    store.record("other", stream, List.of(c));

    int published = relay.relayPending();

    assertThat(published).isEqualTo(3);
    assertThat(destination.published)
        .extracting(e -> e.getEventId())
        .containsExactly(a.getEventId(), b.getEventId(), c.getEventId());
    assertThat(storedEvents.query().where("streamId", Operators.EQUALS, stream).findAll())
        .allMatch(e -> e.getPublishedAt() != null);
    assertThat(relay.relayPending()).isZero();
  }

  @Test
  void aFailureStopsTheBatchAndIsRetriedLater() {
    String stream = UUID.randomUUID().toString();
    CounterIncremented a = new CounterIncremented(stream, 1);
    CounterIncremented b = new CounterIncremented(stream, 2);
    CounterIncremented c = new CounterIncremented(stream, 3);
    store.append("counter", stream, 0, List.of(a, b, c));
    destination.behaviour =
        event -> {
          if (event.getEventId().equals(b.getEventId())) {
            throw new IllegalStateException("broker down");
          }
        };

    assertThat(relay.relayPending()).isEqualTo(1);
    StoredEvent failed =
        storedEvents
            .query()
            .where("eventId", Operators.EQUALS, UUID.fromString(b.getEventId()))
            .findOne()
            .orElseThrow();
    assertThat(failed.getPublishAttempts()).isEqualTo(1);
    assertThat(failed.getLastError()).contains("broker down");
    assertThat(failed.getPublishedAt()).isNull();

    destination.behaviour = event -> {};
    assertThat(relay.relayPending()).isEqualTo(2);
    assertThat(destination.published)
        .extracting(e -> e.getEventId())
        .containsExactly(a.getEventId(), b.getEventId(), c.getEventId());
  }

  @Test
  void restoresTheCorrelationIdOfEachEventWhilePublishing() throws Exception {
    String stream = UUID.randomUUID().toString();
    try (var scope =
        MessageContext.scope(
            MessageContext.empty().with(MessageContext.CORRELATION_ID_KEY, "checkout-9"))) {
      store.append("counter", stream, 0, List.of(new CounterIncremented(stream, 1)));
    }

    relay.relayPending();

    assertThat(destination.correlationIds).containsExactly("checkout-9");
    assertThat(MessageContext.current().isEmpty()).isTrue();
  }

  @Test
  void aPausedRelayKeepsEventsPendingAndPublishesThemInOrderOnResume() {
    String stream = UUID.randomUUID().toString();
    CounterIncremented a = new CounterIncremented(stream, 1);
    CounterIncremented b = new CounterIncremented(stream, 2);
    chaos.set("relay.paused", 1);
    store.append("counter", stream, 0, List.of(a, b));

    assertThat(relay.relayPending()).isZero();
    assertThat(destination.published).isEmpty();

    chaos.set("relay.paused", 0);
    assertThat(relay.relayPending()).isEqualTo(2);
    assertThat(destination.published)
        .extracting(e -> e.getEventId())
        .containsExactly(a.getEventId(), b.getEventId());
  }

  @Test
  void theDuplicateFaultPublishesEveryEventTwice() {
    String stream = UUID.randomUUID().toString();
    CounterIncremented a = new CounterIncremented(stream, 1);
    chaos.set("relay.duplicate", 1);
    store.append("counter", stream, 0, List.of(a));

    assertThat(relay.relayPending()).isEqualTo(1);
    assertThat(destination.published)
        .extracting(e -> e.getEventId())
        .containsExactly(a.getEventId(), a.getEventId());
  }

  @Test
  void theEventIsPublishedInTheTraceThatRecordedIt() {
    String stream = UUID.randomUUID().toString();
    store.append("counter", stream, 0, List.of(new CounterIncremented(stream, 1)));

    relay.relayPending();

    assertThat(traces.resumed)
        .singleElement()
        .satisfies(
            captured ->
                assertThat(captured)
                    .containsEntry(TraceCarrier.TRACEPARENT, RecordingTraceCarrier.TRACE));
    // The trace travels as a trace, not as one more entry of the message context.
    assertThat(destination.contextTraceHeaders).containsExactly("null");
  }

  @Test
  void reportsTheBacklogAndWhatItPublished() {
    String stream = UUID.randomUUID().toString();
    store.append("counter", stream, 0, List.of(new CounterIncremented(stream, 1)));
    double publishedBefore = published();

    assertThat(backlog.pending()).isEqualTo(1);
    assertThat(backlog.oldestAgeSeconds()).isGreaterThanOrEqualTo(0);

    relay.relayPending();

    assertThat(backlog.pending()).isZero();
    assertThat(backlog.oldestAgeSeconds()).isZero();
    assertThat(published()).isEqualTo(publishedBefore + 1);
    assertThat(meters.get("shop.outbox.delay").timer().count()).isPositive();
  }

  @Test
  void countsFailedPublications() {
    String stream = UUID.randomUUID().toString();
    store.append("counter", stream, 0, List.of(new CounterIncremented(stream, 1)));
    destination.behaviour =
        event -> {
          throw new IllegalStateException("broker down");
        };

    relay.relayPending();

    assertThat(meters.get("shop.outbox.failures").counter().count()).isPositive();
    destination.behaviour = event -> {};
  }

  private double published() {
    return meters.find("shop.outbox.published").counters().stream()
        .mapToDouble(c -> c.count())
        .sum();
  }
}
