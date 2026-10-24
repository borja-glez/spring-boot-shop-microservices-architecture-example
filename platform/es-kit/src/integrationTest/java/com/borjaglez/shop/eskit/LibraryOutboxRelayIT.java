package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.jdbc.outbox.OutboxProperties;
import com.borjaglez.cqrs.jdbc.outbox.OutboxRelay;
import com.borjaglez.cqrs.kafka.config.KafkaCqrsProperties;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.borjaglez.shop.eskit.fixtures.CounterIncremented;
import com.borjaglez.shop.eskit.fixtures.TallyEvent;
import com.borjaglez.shop.support.chaos.Chaos;

/**
 * The integration events of the domain events go from the outbox of spring-boot-cqrs to a real
 * Kafka through the library's relay, which publishes through {@code chaosEventBus}.
 */
@DomainEventsIntegrationTest
class LibraryOutboxRelayIT {

  @Autowired DomainEventStore store;
  @Autowired OutboxRelay relay;
  @Autowired Chaos chaos;
  @Autowired KafkaContainer kafka;
  @Autowired KafkaTopicNamingStrategy topicNaming;
  @Autowired KafkaCqrsProperties kafkaProperties;
  @Autowired MessageSerializer serializer;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired OutboxProperties outboxProperties;

  private KafkaConsumer<String, byte[]> consumer;

  @BeforeEach
  void subscribe() {
    chaos.reset();
    consumer =
        new KafkaConsumer<>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "library-outbox-relay-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"),
            new StringDeserializer(),
            new ByteArrayDeserializer());
    consumer.subscribe(List.of(topicNaming.topic(kafkaProperties.getEvents().getTopic())));
  }

  @AfterEach
  void close() {
    chaos.reset();
    consumer.close();
  }

  private static String newTally() {
    return UUID.randomUUID().toString();
  }

  private static TallyEvent.Incremented incremented(String tally, int amount) {
    return new TallyEvent.Incremented(tally, amount, Instant.now());
  }

  private void record(String tally, List<TallyEvent> events) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(status -> store.record("tally", tally, events));
  }

  /** Runs the relay until it has nothing left, as its scheduler does. */
  private void relayAll() {
    OutboxRelay.BatchResult batch;
    do {
      batch = relay.relayBatch();
    } while (batch.drainAgain());
  }

  /** Reads the events topic until it has seen {@code count} integration events of the tally. */
  private List<CounterIncremented> received(String tally, int count) {
    List<CounterIncremented> received = new ArrayList<>();
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInSameThread()
        .until(
            () -> {
              received.addAll(poll(tally));
              return received.size() >= count;
            });
    return received;
  }

  /**
   * Reads the events topic until it has seen {@code count} integration events of the tally, then
   * until no other one arrives for {@code quiet}.
   */
  private List<CounterIncremented> receivedUntilQuiet(String tally, int count, Duration quiet) {
    List<CounterIncremented> received = received(tally, count);
    Instant lastArrival = Instant.now();
    while (Duration.between(lastArrival, Instant.now()).compareTo(quiet) < 0) {
      List<CounterIncremented> more = poll(tally);
      if (!more.isEmpty()) {
        received.addAll(more);
        lastArrival = Instant.now();
      }
    }
    return received;
  }

  /** The integration events of the tally in one poll of the events topic. */
  private List<CounterIncremented> poll(String tally) {
    List<CounterIncremented> events = new ArrayList<>();
    for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(200))) {
      Header type = record.headers().lastHeader(KafkaMessageHeaders.PAYLOAD_TYPE);
      if (type != null
          && CounterIncremented.class
              .getName()
              .equals(new String(type.value(), StandardCharsets.UTF_8))) {
        CounterIncremented event = serializer.deserialize(record.value(), CounterIncremented.class);
        if (event.getCounterId().equals(tally)) {
          events.add(event);
        }
      }
    }
    return events;
  }

  private List<Map<String, Object>> outboxRows(String tally) {
    return jdbc.queryForList(
        "select event_id, published_at, failed_at, read_failures from cqrs_outbox"
            + " where convert_from(payload, 'UTF8')::jsonb ->> 'counterId' = ? order by id",
        tally);
  }

  @Test
  void translatedEventsReachKafka() {
    String tally = newTally();
    record(
        tally,
        List.of(
            new TallyEvent.Opened(tally, Instant.now()),
            incremented(tally, 1),
            incremented(tally, 2)));

    relayAll();

    assertThat(relay.target()).isInstanceOf(ChaosEventBus.class);
    assertThat(received(tally, 2)).extracting(CounterIncremented::getAmount).containsExactly(1, 2);
    assertThat(outboxRows(tally))
        .allSatisfy(row -> assertThat(row.get("published_at")).isNotNull());
  }

  @Test
  void chaosPausedRelayPublishesAfterResume() {
    String tally = newTally();
    chaos.set(RelayFaults.PAUSED, 1);
    record(tally, List.of(incremented(tally, 1), incremented(tally, 2)));

    // A pause longer than the attempts after which an unreadable row would be set aside.
    int maxAttempts = outboxProperties.getRelay().getMaxAttempts();
    for (int run = 0; run <= maxAttempts; run++) {
      OutboxRelay.BatchResult paused = relay.relayBatch();
      assertThat(paused.stopped()).isTrue();
      assertThat(paused.published()).isZero();
      assertThat(paused.setAside()).isZero();
    }
    assertThat(outboxRows(tally))
        .hasSize(2)
        .allSatisfy(
            row -> {
              assertThat(row.get("published_at")).isNull();
              assertThat(row.get("failed_at")).isNull();
              assertThat(row).containsEntry("read_failures", 0);
            });

    chaos.set(RelayFaults.PAUSED, 0);
    relayAll();

    assertThat(received(tally, 2)).extracting(CounterIncremented::getAmount).containsExactly(1, 2);
    assertThat(outboxRows(tally))
        .allSatisfy(row -> assertThat(row.get("published_at")).isNotNull());
  }

  @Test
  void chaosDuplicateRelayPublishesTwice() {
    String tally = newTally();
    chaos.set(RelayFaults.DUPLICATE, 1);
    record(tally, List.of(incremented(tally, 5)));

    relayAll();

    // Two copies, and no third one while the topic stays quiet.
    assertThat(receivedUntilQuiet(tally, 2, Duration.ofSeconds(3)))
        .hasSize(2)
        .extracting(Event::getEventId)
        .containsOnly((String) outboxRows(tally).getFirst().get("event_id"));
  }
}
