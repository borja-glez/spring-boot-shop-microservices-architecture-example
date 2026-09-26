package com.borjaglez.shop.notifications;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * PostgreSQL and Kafka for the integration tests. The shared test-support module is built against
 * Spring Boot 4, so this Boot 3 service declares its own containers (same images).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestContainers {

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgres() {
    return new PostgreSQLContainer<>("postgres:17-alpine");
  }

  @Bean
  @ServiceConnection
  KafkaContainer kafka() {
    return new KafkaContainer("apache/kafka:4.3.1");
  }
}
