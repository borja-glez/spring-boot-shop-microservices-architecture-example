package com.borjaglez.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/** The health probes Kubernetes reads. */
@CatalogComponentTest
class HealthProbesComponentTest {

  @LocalServerPort int port;

  CatalogClient catalog;

  @BeforeEach
  void client() {
    catalog = new CatalogClient(port);
  }

  @Test
  void theHealthProbesAreExposed() {
    assertThat(catalog.get("/actuator/health/readiness").getBody()).containsEntry("status", "UP");
    assertThat(catalog.get("/actuator/health/liveness").getBody()).containsEntry("status", "UP");
  }
}
