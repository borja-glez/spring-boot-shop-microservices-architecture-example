package com.borjaglez.shop.catalog;

import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;

/**
 * The events topic of the platform as the other services read it: a consumer of its own reads every
 * record from the beginning, so a test finds the events it caused, and the seed's, whatever ran
 * before it. The headers it reads are those of {@code platform/api-contracts/asyncapi/common.yaml}.
 */
class EventsTopic implements AutoCloseable {

  /** The topic of the integration events ({@code cqrs.kafka.prefix=shop}). */
  static final String NAME = "shop.events";

  private static final Duration PATIENCE = Duration.ofSeconds(30);

  /**
   * An event as it arrived: the record key, the logical name and the correlation id of its headers,
   * the payload as JSON and the event the application's serializer reads from it.
   */
  record Received<E extends Event>(
      String key, String name, String correlationId, Map<String, Object> json, E event) {}

  private record Raw(String key, String name, String correlationId, byte[] payload) {}

  private final KafkaConsumer<String, byte[]> consumer;
  private final MessageSerializer serializer;
  private final MessageNamingStrategy naming;
  private final List<Raw> records = new ArrayList<>();

  EventsTopic(
      List<String> bootstrapServers, MessageSerializer serializer, MessageNamingStrategy naming) {
    this.serializer = serializer;
    this.naming = naming;
    this.consumer =
        new KafkaConsumer<>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                String.join(",", bootstrapServers),
                ConsumerConfig.GROUP_ID_CONFIG,
                "catalog-component-tests",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false),
            new StringDeserializer(),
            new ByteArrayDeserializer());
    consumer.subscribe(List.of(NAME));
  }

  /** The events of the type received so far. */
  synchronized <E extends Event> List<Received<E>> received(Class<E> type) {
    for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(200))) {
      records.add(
          new Raw(
              record.key(),
              header(record, "cqrs.message.name"),
              header(record, "cqrs.context.correlationId"),
              record.value()));
    }
    String name = naming.eventName(type);
    return records.stream()
        .filter(raw -> name.equals(raw.name()))
        .map(raw -> read(raw, type))
        .toList();
  }

  /** Waits for the events of the type that match, and returns those received by then. */
  <E extends Event> List<Received<E>> awaitReceived(Class<E> type, Predicate<E> matching) {
    return await()
        .atMost(PATIENCE)
        .pollInSameThread()
        .until(
            () ->
                received(type).stream()
                    .filter(received -> matching.test(received.event()))
                    .toList(),
            found -> !found.isEmpty());
  }

  @SuppressWarnings("unchecked")
  private <E extends Event> Received<E> read(Raw raw, Class<E> type) {
    return new Received<>(
        raw.key(),
        raw.name(),
        raw.correlationId(),
        serializer.deserialize(raw.payload(), Map.class),
        serializer.deserialize(raw.payload(), type));
  }

  private static String header(ConsumerRecord<String, byte[]> record, String name) {
    Header header = record.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }

  @Override
  public synchronized void close() {
    consumer.close();
  }
}
