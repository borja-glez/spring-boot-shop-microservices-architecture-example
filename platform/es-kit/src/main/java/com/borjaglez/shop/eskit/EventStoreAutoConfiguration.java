package com.borjaglez.shop.eskit;

import java.time.Clock;

import jakarta.persistence.EntityManager;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.idempotency.IdempotencyStore;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.borjaglez.shop.eskit.kafka.KafkaClientMetricsPostProcessor;
import com.borjaglez.shop.support.chaos.Chaos;
import com.borjaglez.shop.support.tracing.TraceCarrier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;

/**
 * Wires the event store, the relay and the metrics of the idempotent consumers. The entities and
 * repositories of this module are added to the application's auto-configuration packages, so no
 * {@code @EntityScan} is needed. Services add {@code classpath:db/eskit} to their Flyway locations.
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
      "com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration",
      "com.borjaglez.cqrs.autoconfigure.CqrsSerializationAutoConfiguration",
      "com.borjaglez.cqrs.jdbc.CqrsJdbcIdempotencyAutoConfiguration"
    },
    beforeName = "com.borjaglez.cqrs.autoconfigure.CqrsIdempotencyAutoConfiguration")
@AutoConfigurationPackage
@EnableConfigurationProperties(EventStoreProperties.class)
@ImportRuntimeHints(EsKitRuntimeHints.class)
public class EventStoreAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  Clock eventStoreClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean
  EventTypeRegistry eventTypeRegistry(
      EventStoreProperties properties, MessageNamingStrategy namingStrategy) {
    return EventTypeRegistry.scanning(properties.eventStore().eventPackages(), namingStrategy);
  }

  @Bean
  @ConditionalOnMissingBean
  EventStore eventStore(
      StoredEventRepository repository,
      EventTypeRegistry types,
      MessageSerializer serializer,
      Clock clock,
      ObjectProvider<TraceCarrier> traces) {
    return new EventStore(
        repository, types, serializer, clock, traces.getIfAvailable(() -> TraceCarrier.NONE));
  }

  @Bean
  @ConditionalOnMissingBean
  ConsumerMetrics consumerMetrics(Clock clock, ObjectProvider<MeterRegistry> meters) {
    return new ConsumerMetrics(clock, meters(meters));
  }

  /** The {@code @Idempotent} handlers go through the JDBC store, measured. */
  @Bean
  @ConditionalOnMissingBean
  IdempotentInvoker idempotentInvoker(IdempotencyStore store, ConsumerMetrics metrics) {
    return new IdempotentInvoker(metrics.meter(store));
  }

  @Bean
  @ConditionalOnBean(OutboxDestination.class)
  @ConditionalOnMissingBean
  OutboxRelay outboxRelay(
      EntityManager entityManager,
      PlatformTransactionManager transactionManager,
      EventStore eventStore,
      OutboxDestination destination,
      Clock clock,
      EventStoreProperties properties,
      ObjectProvider<Chaos> chaos,
      ObjectProvider<TraceCarrier> traces,
      ObjectProvider<MeterRegistry> meters) {
    return new OutboxRelay(
        entityManager,
        new TransactionTemplate(transactionManager),
        eventStore,
        destination,
        clock,
        properties.outbox().relay().batchSize(),
        chaos.getIfAvailable(Chaos::new),
        traces.getIfAvailable(() -> TraceCarrier.NONE),
        meters(meters));
  }

  @Bean
  @ConditionalOnBean(OutboxRelay.class)
  @ConditionalOnClass(name = "io.micrometer.core.instrument.binder.MeterBinder")
  OutboxMetrics outboxMetrics(StoredEventRepository events, Clock clock) {
    return new OutboxMetrics(events, clock);
  }

  @Bean
  @ConditionalOnBean(OutboxRelay.class)
  @ConditionalOnBooleanProperty(name = "shop.outbox.relay.enabled", matchIfMissing = true)
  SmartLifecycle outboxRelayScheduler(OutboxRelay relay, EventStoreProperties properties) {
    return new OutboxRelayScheduler(relay, properties.outbox().relay().interval());
  }

  /** Without a registry (plain tests), metrics go to Micrometer's empty global registry. */
  private static MeterRegistry meters(ObjectProvider<MeterRegistry> meters) {
    return meters.getIfAvailable(() -> Metrics.globalRegistry);
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.kafka.core.MicrometerConsumerListener")
  static class KafkaClientMetrics {

    @Bean
    static KafkaClientMetricsPostProcessor kafkaClientMetricsPostProcessor(
        ObjectProvider<MeterRegistry> meters) {
      return new KafkaClientMetricsPostProcessor(meters);
    }
  }
}
