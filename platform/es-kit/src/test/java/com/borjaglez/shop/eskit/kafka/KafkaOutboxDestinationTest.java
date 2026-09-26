package com.borjaglez.shop.eskit.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.kafka.config.KafkaCqrsProperties;
import com.borjaglez.cqrs.kafka.infrastructure.DefaultKafkaTopicNamingStrategy;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.shop.eskit.fixtures.CounterIncremented;

class KafkaOutboxDestinationTest {

  private final KafkaMessagePublisher publisher = mock(KafkaMessagePublisher.class);
  private final KafkaTopicNamingStrategy naming = new DefaultKafkaTopicNamingStrategy("shop");

  @Test
  void publishesOnTheEventsTopic() {
    var destination = new KafkaOutboxDestination(publisher, naming, new KafkaCqrsProperties());
    var event = new CounterIncremented("c-1", 1);

    destination.publish(event);

    assertThat(destination.topic()).isEqualTo("shop.events");
    verify(publisher).publish("shop.events", event);
  }

  @Test
  void aKafkaFailureReachesTheRelay() {
    doThrow(new IllegalStateException("broker down")).when(publisher).publish(anyString(), any());
    var destination = new KafkaOutboxDestination(publisher, naming, new KafkaCqrsProperties());

    assertThatThrownBy(() -> destination.publish(new CounterIncremented("c-1", 1)))
        .hasMessage("broker down");
  }
}
