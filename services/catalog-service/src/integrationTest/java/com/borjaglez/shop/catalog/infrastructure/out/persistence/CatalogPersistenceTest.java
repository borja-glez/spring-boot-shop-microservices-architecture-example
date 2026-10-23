package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration;
import com.borjaglez.cqrs.autoconfigure.CqrsSerializationAutoConfiguration;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.jdbc.CqrsJdbcIdempotencyAutoConfiguration;
import com.borjaglez.cqrs.jdbc.outbox.CqrsJdbcOutboxAutoConfiguration;
import com.borjaglez.shop.catalog.infrastructure.config.CatalogConfiguration;
import com.borjaglez.shop.catalog.infrastructure.config.CatalogEventTypes;
import com.borjaglez.shop.eskit.EventStoreAutoConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.specrepository.boot4.SpecificationRepositoryAutoConfiguration;

/**
 * The persistence adapters of the catalog on PostgreSQL, as the application wires them: the
 * migrations, specification-repository, es-kit's {@code DomainEventStore} and the outbox of
 * spring-boot-cqrs, each through its own auto-configuration, plus the catalog's composition root.
 * The outbox relay is off, so whatever the tests write stays in {@code cqrs_outbox} and Kafka is
 * not needed. Every test class annotated with it shares one cached application context, and so one
 * database: tests that commit use products of their own.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest(
    properties = {
      "cqrs.outbox.enabled=true",
      "cqrs.outbox.relay.enabled=false",
      "cqrs.jdbc.outbox.cleanup-enabled=false",
      "cqrs.jdbc.idempotency.cleanup-enabled=false"
    })
@ImportAutoConfiguration({
  JacksonAutoConfiguration.class,
  SpecificationRepositoryAutoConfiguration.class,
  CqrsAutoConfiguration.class,
  CqrsSerializationAutoConfiguration.class,
  CqrsJdbcIdempotencyAutoConfiguration.class,
  CqrsJdbcOutboxAutoConfiguration.class,
  EventStoreAutoConfiguration.class
})
@Import({
  PostgresTestConfiguration.class,
  CatalogEventTypes.class,
  CatalogConfiguration.class,
  CatalogPersistenceTest.WithoutKafka.class
})
@interface CatalogPersistenceTest {

  /**
   * es-kit decorates the Kafka event bus for the relay of the outbox; with the relay off, this
   * stand-in only has to exist.
   */
  @TestConfiguration(proxyBeanMethods = false)
  class WithoutKafka {

    @Bean
    EventBus kafkaEventBus() {
      return new EventBus() {
        @Override
        public void publish(Event event) {
          throw new UnsupportedOperationException("Kafka is not part of the persistence tests");
        }

        @Override
        public void publish(List<Event> events) {
          events.forEach(this::publish);
        }
      };
    }
  }
}
