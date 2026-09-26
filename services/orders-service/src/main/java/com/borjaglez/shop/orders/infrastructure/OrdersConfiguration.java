package com.borjaglez.shop.orders.infrastructure;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.kafka.config.KafkaCqrsProperties;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.shop.eskit.AggregateStore;
import com.borjaglez.shop.eskit.EventStore;
import com.borjaglez.shop.eskit.OutboxDestination;
import com.borjaglez.shop.eskit.kafka.KafkaOutboxDestination;
import com.borjaglez.shop.orders.domain.Order;

/** Infrastructure beans of the orders service. */
@Configuration(proxyBeanMethods = false)
class OrdersConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** Orders are stored as event streams of type {@value Order#STREAM_TYPE}. */
  @Bean
  AggregateStore<Order> orderStore(EventStore eventStore) {
    return new AggregateStore<>(eventStore, Order.STREAM_TYPE, Order::new);
  }

  /**
   * The outbox relay publishes order events on Kafka straight through {@code
   * KafkaMessagePublisher}, so a broker failure surfaces as an exception to the relay, which keeps
   * the row pending and retries.
   */
  @Bean
  OutboxDestination outboxDestination(
      KafkaMessagePublisher publisher,
      KafkaTopicNamingStrategy topicNaming,
      KafkaCqrsProperties properties) {
    return new KafkaOutboxDestination(publisher, topicNaming, properties);
  }
}
