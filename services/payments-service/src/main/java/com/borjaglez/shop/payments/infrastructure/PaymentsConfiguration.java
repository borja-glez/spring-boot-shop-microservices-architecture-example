package com.borjaglez.shop.payments.infrastructure;

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
import com.borjaglez.shop.payments.domain.Payment;

/** Infrastructure beans of the payments service. */
@Configuration(proxyBeanMethods = false)
class PaymentsConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** Payments are stored as event streams of type {@value Payment#STREAM_TYPE}. */
  @Bean
  AggregateStore<Payment> paymentStore(EventStore eventStore) {
    return new AggregateStore<>(eventStore, Payment.STREAM_TYPE, Payment::new);
  }

  /**
   * The outbox relay publishes payment events on Kafka straight through {@code
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
