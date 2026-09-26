package com.borjaglez.shop.eskit.kafka;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.core.MicrometerProducerListener;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Binds Micrometer's Kafka client metrics to every Kafka client factory of the service, including
 * the ones spring-boot-cqrs creates for its buses (Spring Boot only instruments its own). The
 * consumer metrics include {@code kafka.consumer.fetch.manager.records.lag.max}: how far a
 * consumer, and so the read models it feeds, is behind the log.
 */
public class KafkaClientMetricsPostProcessor implements BeanPostProcessor {

  private final ObjectProvider<MeterRegistry> meters;

  public KafkaClientMetricsPostProcessor(ObjectProvider<MeterRegistry> meters) {
    this.meters = meters;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof DefaultKafkaConsumerFactory<?, ?> consumers
        && consumers.getListeners().stream()
            .noneMatch(MicrometerConsumerListener.class::isInstance)) {
      meters.ifAvailable(
          registry -> consumers.addListener(new MicrometerConsumerListener<>(registry)));
    } else if (bean instanceof DefaultKafkaProducerFactory<?, ?> producers
        && producers.getListeners().stream()
            .noneMatch(MicrometerProducerListener.class::isInstance)) {
      meters.ifAvailable(
          registry -> producers.addListener(new MicrometerProducerListener<>(registry)));
    }
    return bean;
  }
}
