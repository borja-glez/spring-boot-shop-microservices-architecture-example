package com.borjaglez.shop.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * The routes of application.yaml, with every service replaced by a stub: the chaos API of a service
 * is under {@code /api/v1/chaos/<service>}, which reaches that service, while the service's own
 * {@code /api/v1/<service>/...} paths keep reaching it through its versioned route.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChaosRoutesIT {

  private static final List<String> CHAOS_SERVICES =
      List.of("catalog", "orders", "inventory", "payments");

  static Map<String, StubService> services;

  @DynamicPropertySource
  static void services(DynamicPropertyRegistry registry) throws IOException {
    services =
        Map.of(
            "catalog", new StubService(),
            "orders", new StubService(),
            "inventory", new StubService(),
            "payments", new StubService());
    services.forEach((name, stub) -> registry.add(name.toUpperCase() + "_URI", stub::baseUrl));
  }

  @AfterAll
  static void stop() {
    services.values().forEach(StubService::close);
  }

  @LocalServerPort int port;
  RestClient http;

  @BeforeEach
  void client() {
    services.values().forEach(stub -> stub.received().clear());
    http =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();
  }

  @Test
  void theChaosApiOfAServiceReachesThatService() {
    for (String service : CHAOS_SERVICES) {
      http.get().uri("/api/v1/chaos/{service}", service).retrieve().toBodilessEntity();
      http.put()
          .uri("/api/v1/chaos/{service}/{key}", service, "relay.paused")
          .body(Map.of("value", 1))
          .retrieve()
          .toBodilessEntity();
      http.delete().uri("/api/v1/chaos/{service}", service).retrieve().toBodilessEntity();

      assertThat(services.get(service).received())
          .extracting(StubService.Received::method, StubService.Received::pathAndQuery)
          .containsExactly(
              tuple("GET", "/api/v1/chaos/" + service),
              tuple("PUT", "/api/v1/chaos/" + service + "/relay.paused"),
              tuple("DELETE", "/api/v1/chaos/" + service));
    }
  }

  @Test
  void theOwnPathsOfAServiceStillReachIt() {
    for (String service : CHAOS_SERVICES) {
      http.get().uri("/api/v1/{service}/{id}", service, "42").retrieve().toBodilessEntity();

      assertThat(services.get(service).received())
          .singleElement()
          .satisfies(r -> assertThat(r.pathAndQuery()).isEqualTo("/api/v1/" + service + "/42"));
    }
  }
}
