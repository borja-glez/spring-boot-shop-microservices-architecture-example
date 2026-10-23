package com.borjaglez.shop.eskit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;

/**
 * The application of a service whose aggregates emit domain events ({@link
 * DomainEventsTestConfiguration}), on Postgres and Kafka, with the outbox of spring-boot-cqrs
 * relaying through {@code chaosEventBus}. The tests drive the relay. Every test class annotated
 * with it shares one cached application context.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(
    properties = {
      "cqrs.outbox.enabled=true",
      "cqrs.outbox.relay.event-bus=chaosEventBus",
      "cqrs.outbox.relay.interval=1h"
    })
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  DomainEventsTestConfiguration.class
})
@interface DomainEventsIntegrationTest {}
