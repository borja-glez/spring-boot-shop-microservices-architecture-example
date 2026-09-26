package com.borjaglez.shop.inventory.infrastructure;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.kafka.config.KafkaCqrsProperties;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.shop.eskit.OutboxDestination;
import com.borjaglez.shop.eskit.kafka.KafkaOutboxDestination;

/** Infrastructure beans of the inventory service. */
@Configuration(proxyBeanMethods = false)
class InventoryConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * The outbox relay publishes stock events on Kafka straight through {@code
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
