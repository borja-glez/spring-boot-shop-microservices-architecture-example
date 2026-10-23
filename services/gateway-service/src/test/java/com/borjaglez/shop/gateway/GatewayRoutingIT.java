package com.borjaglez.shop.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIT {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};

  static StubService catalog;
  static StubService ordersV1;
  static StubService ordersV2;

  @DynamicPropertySource
  static void routes(DynamicPropertyRegistry registry) throws IOException {
    catalog = new StubService();
    ordersV1 = new StubService();
    ordersV2 = new StubService();
    registry.add("shop.gateway.routes[0].id", () -> "catalog");
    registry.add("shop.gateway.routes[0].path", () -> "/api/catalog/**");
    registry.add("shop.gateway.routes[0].uri", catalog::baseUrl);
    registry.add("shop.gateway.routes[1].id", () -> "orders");
    registry.add("shop.gateway.routes[1].path", () -> "/api/orders/**");
    // Nothing listens on port 9: the orders service is "down".
    registry.add("shop.gateway.routes[1].uri", () -> "http://localhost:9");
    registry.add("shop.gateway.routes[2].id", () -> "catalog-v1");
    registry.add("shop.gateway.routes[2].path", () -> "/api/v1/catalog/**");
    registry.add("shop.gateway.routes[2].uri", catalog::baseUrl);
    registry.add("shop.gateway.routes[3].id", () -> "orders-v1");
    registry.add("shop.gateway.routes[3].path", () -> "/api/v1/orders/**");
    registry.add("shop.gateway.routes[3].uri", ordersV1::baseUrl);
    registry.add("shop.gateway.routes[4].id", () -> "orders-v2");
    registry.add("shop.gateway.routes[4].path", () -> "/api/v2/orders/**");
    registry.add("shop.gateway.routes[4].uri", ordersV2::baseUrl);
  }

  @AfterAll
  static void stop() {
    catalog.close();
    ordersV1.close();
    ordersV2.close();
  }

  @LocalServerPort int port;
  RestClient http;

  @BeforeEach
  void client() {
    catalog.received().clear();
    ordersV1.received().clear();
    ordersV2.received().clear();
    http =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();
  }

  @Test
  void forwardsPathAndQueryToTheService() {
    ResponseEntity<Map<String, Object>> response =
        http.get()
            .uri(
                URI.create(
                    "http://localhost:"
                        + port
                        + "/api/catalog/products?filter=name:contains:caf%C3%A9&page=1"))
            .retrieve()
            .toEntity(JSON);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("from", "stub");
    assertThat(catalog.received())
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.method()).isEqualTo("GET");
              assertThat(URLDecoder.decode(r.pathAndQuery(), StandardCharsets.UTF_8))
                  .isEqualTo("/api/catalog/products?filter=name:contains:café&page=1");
            });
  }

  @Test
  void forwardsTheUserHeader() {
    http.post()
        .uri("/api/catalog/products/1/publish")
        .header("X-Shop-User", "seller-ana")
        .retrieve()
        .toBodilessEntity();

    assertThat(catalog.received().getFirst().header("X-Shop-User")).isEqualTo("seller-ana");
  }

  @Test
  void generatedCorrelationIdReachesTheServiceAndTheClient() {
    var response = http.get().uri("/api/catalog/categories").retrieve().toBodilessEntity();

    String returned = response.getHeaders().getFirst("X-Correlation-Id");
    assertThat(returned).isNotBlank();
    assertThat(response.getHeaders().get("X-Correlation-Id")).hasSize(1);
    assertThat(catalog.received().getFirst().header("X-Correlation-Id")).isEqualTo(returned);
  }

  @Test
  void clientCorrelationIdIsKept() {
    http.get()
        .uri("/api/catalog/categories")
        .header("X-Correlation-Id", "ui-123")
        .retrieve()
        .toBodilessEntity();

    assertThat(catalog.received().getFirst().header("X-Correlation-Id")).isEqualTo("ui-123");
  }

  @Test
  void unknownPathsAreNotFoundProblems() {
    var response = http.get().uri("/api/nothing-here").retrieve().toEntity(JSON);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsEntry("code", "not-found");
    assertThat(catalog.received()).isEmpty();
  }

  @Test
  void unreachableServiceIsAServiceUnavailableProblem() {
    var response = http.get().uri("/api/orders/mine").retrieve().toEntity(JSON);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).containsEntry("code", "service-unavailable");
  }

  @Test
  void versionedRoutesForwardTheUnchangedPathToTheirService() {
    http.get().uri("/api/v1/catalog/products").retrieve().toBodilessEntity();
    http.get().uri("/api/v1/orders/mine").retrieve().toBodilessEntity();
    http.get().uri("/api/v2/orders/mine").retrieve().toBodilessEntity();

    assertThat(catalog.received())
        .singleElement()
        .satisfies(r -> assertThat(r.pathAndQuery()).isEqualTo("/api/v1/catalog/products"));
    assertThat(ordersV1.received())
        .singleElement()
        .satisfies(r -> assertThat(r.pathAndQuery()).isEqualTo("/api/v1/orders/mine"));
    assertThat(ordersV2.received())
        .singleElement()
        .satisfies(r -> assertThat(r.pathAndQuery()).isEqualTo("/api/v2/orders/mine"));
  }
}
