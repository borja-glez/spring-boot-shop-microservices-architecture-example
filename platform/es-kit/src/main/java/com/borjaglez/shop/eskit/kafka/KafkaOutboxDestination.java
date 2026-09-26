package com.borjaglez.shop.eskit.kafka;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.kafka.config.KafkaCqrsProperties;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.shop.eskit.OutboxDestination;

/**
 * Publishes outbox events on the events topic of spring-boot-cqrs, waiting for Kafka to acknowledge
 * each one.
 *
 * <p>It publishes straight through {@code KafkaMessagePublisher} rather than an event bus, so a
 * broker failure surfaces as an exception to the relay, which keeps the row pending and retries it
 * on its next run. An event is only marked as published once Kafka has acknowledged it.
 */
public class KafkaOutboxDestination implements OutboxDestination {

  private final KafkaMessagePublisher publisher;
  private final String topic;

  public KafkaOutboxDestination(
      KafkaMessagePublisher publisher,
      KafkaTopicNamingStrategy topicNaming,
      KafkaCqrsProperties properties) {
    this.publisher = publisher;
    this.topic = topicNaming.topic(properties.getEvents().getTopic());
  }

  @Override
  public void publish(Event event) {
    publisher.publish(topic, event);
  }

  public String topic() {
    return topic;
  }
}
