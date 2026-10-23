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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.jdbc.outbox.OutboxRelay;
import com.borjaglez.cqrs.kafka.config.KafkaCqrsProperties;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.borjaglez.shop.eskit.fixtures.CounterIncremented;
import com.borjaglez.shop.eskit.fixtures.TallyEvent;
import com.borjaglez.shop.support.chaos.Chaos;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;

/**
 * The integration events of the domain events go from the outbox of spring-boot-cqrs to a real
 * Kafka through the library's relay, which publishes through {@code chaosEventBus}.
 */
@SpringBootTest(
    properties = {
      "cqrs.outbox.enabled=true",
      "cqrs.outbox.relay.event-bus=chaosEventBus",
      // The tests drive the relay.
      "cqrs.outbox.relay.interval=1h"
    })
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  DomainEventsTestConfiguration.class
})
class LibraryOutboxRelayIT {

  /** Default {@code cqrs.outbox.relay.max-attempts}: unreadable rows are set aside after it. */
  private static final int MAX_ATTEMPTS = 10;

  @Autowired DomainEventStore store;
  @Autowired OutboxRelay relay;
  @Autowired Chaos chaos;
  @Autowired KafkaContainer kafka;
  @Autowired KafkaTopicNamingStrategy topicNaming;
  @Autowired KafkaCqrsProperties kafkaProperties;
  @Autowired MessageSerializer serializer;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactionManager;

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
              for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(200))) {
                Header type = record.headers().lastHeader(KafkaMessageHeaders.PAYLOAD_TYPE);
                if (type != null
                    && CounterIncremented.class
                        .getName()
                        .equals(new String(type.value(), StandardCharsets.UTF_8))) {
                  CounterIncremented event =
                      serializer.deserialize(record.value(), CounterIncremented.class);
                  if (event.getCounterId().equals(tally)) {
                    received.add(event);
                  }
                }
              }
              return received.size() >= count;
            });
    return received;
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
    chaos.set("relay.paused", 1);
    record(tally, List.of(incremented(tally, 1), incremented(tally, 2)));

    // A pause longer than the attempts after which an unreadable row would be set aside.
    for (int run = 0; run <= MAX_ATTEMPTS; run++) {
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

    chaos.set("relay.paused", 0);
    relayAll();

    assertThat(received(tally, 2)).extracting(CounterIncremented::getAmount).containsExactly(1, 2);
    assertThat(outboxRows(tally))
        .allSatisfy(row -> assertThat(row.get("published_at")).isNotNull());
  }

  @Test
  void chaosDuplicateRelayPublishesTwice() {
    String tally = newTally();
    chaos.set("relay.duplicate", 1);
    record(tally, List.of(incremented(tally, 5)));

    relayAll();

    assertThat(received(tally, 2))
        .hasSize(2)
        .extracting(Event::getEventId)
        .containsOnly((String) outboxRows(tally).getFirst().get("event_id"));
  }
}
