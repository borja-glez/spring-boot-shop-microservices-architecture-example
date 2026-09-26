package com.borjaglez.shop.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts the same Kafka version (KRaft) used by Compose and Kubernetes and wires it into the
 * application context. Import it with {@code @Import(KafkaTestConfiguration.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestConfiguration {

  /** Keep in sync with deploy/compose and deploy/k8s. */
  public static final DockerImageName IMAGE = DockerImageName.parse("apache/kafka:4.3.1");

  @Bean
  @ServiceConnection
  KafkaContainer kafka() {
    return new KafkaContainer(IMAGE);
  }
}
