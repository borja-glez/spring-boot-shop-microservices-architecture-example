package com.borjaglez.shop.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts the same PostgreSQL version used by Compose and Kubernetes and wires it into the
 * application context through {@link ServiceConnection}. Import it with
 * {@code @Import(PostgresTestConfiguration.class)}.
 *
 * <p>Spring's test context cache keeps one container per distinct context configuration, so test
 * classes that share a configuration share a database.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

  /** Keep in sync with deploy/compose and deploy/k8s. */
  public static final DockerImageName IMAGE = DockerImageName.parse("postgres:17-alpine");

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer(IMAGE).withDatabaseName("shop").withUsername("shop");
  }
}
