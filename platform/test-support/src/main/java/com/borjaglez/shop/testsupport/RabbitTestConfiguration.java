package com.borjaglez.shop.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts the same RabbitMQ version used by Compose and Kubernetes and wires it into the application
 * context. Import it with {@code @Import(RabbitTestConfiguration.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitTestConfiguration {

  /** Keep in sync with deploy/compose and deploy/k8s. */
  public static final DockerImageName IMAGE =
      DockerImageName.parse("rabbitmq:4.3-management-alpine").asCompatibleSubstituteFor("rabbitmq");

  @Bean
  @ServiceConnection
  RabbitMQContainer rabbitmq() {
    return new RabbitMQContainer(IMAGE);
  }
}
