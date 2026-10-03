package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.shop.eskit.fixtures.CounterIncremented;
import com.borjaglez.shop.eskit.fixtures.CounterProjector;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * The {@code @Idempotent} handlers of the services: markers in the {@code cqrs_processed_message}
 * table of {@code db/eskit}, and the {@code shop.consumer.*} metrics.
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
class IdempotentConsumersIT {

  @Autowired EventHandlerRegistry handlers;
  @Autowired ConsumerMetrics metrics;
  @Autowired CounterProjector projector;
  @Autowired JdbcTemplate jdbc;
  @Autowired MeterRegistry meters;

  @BeforeEach
  void reset() {
    projector.applied.clear();
    projector.failing = false;
  }

  /** Dispatches as the consumers do: through the middleware, then the handlers. */
  private void deliver(CounterIncremented event) throws Exception {
    metrics.process(
        event,
        message -> {
          handlers.handle((CounterIncremented) message);
          return null;
        });
  }

  private int markers(String eventId) {
    return jdbc.queryForObject(
        "select count(*) from cqrs_processed_message where handler_id = ? and message_id = ?",
        Integer.class,
        CounterProjector.CONSUMER,
        eventId);
  }

  private double count(String outcome) {
    return meters
        .counter("shop.consumer.events", "consumer", CounterProjector.CONSUMER, "outcome", outcome)
        .count();
  }

  private long lags() {
    return meters.timer("shop.consumer.lag", "consumer", CounterProjector.CONSUMER).count();
  }

  @Test
  void aRedeliveredEventIsAppliedOnce() throws Exception {
    CounterIncremented event = new CounterIncremented(UUID.randomUUID().toString(), 1);

    deliver(event);
    deliver(event);

    assertThat(projector.applied).containsExactly(event.getEventId());
    assertThat(markers(event.getEventId())).isEqualTo(1);
  }

  @Test
  void aFailedEventLeavesNoMarkerAndIsAppliedWhenRedelivered() throws Exception {
    CounterIncremented event = new CounterIncremented(UUID.randomUUID().toString(), 1);
    projector.failing = true;

    assertThatThrownBy(() -> deliver(event)).hasMessageContaining("projection failed");
    assertThat(markers(event.getEventId())).isZero();

    projector.failing = false;
    deliver(event);

    assertThat(projector.applied).containsExactly(event.getEventId());
  }

  @Test
  void countsAppliedAndDuplicateEventsAndTimesHowLateTheyArrive() throws Exception {
    double applied = count("applied");
    double duplicates = count("duplicate");
    long lags = lags();
    CounterIncremented event = new CounterIncremented(UUID.randomUUID().toString(), 1);

    deliver(event);
    deliver(event);

    assertThat(count("applied")).isEqualTo(applied + 1);
    assertThat(count("duplicate")).isEqualTo(duplicates + 1);
    assertThat(lags()).isEqualTo(lags + 1);
  }
}
