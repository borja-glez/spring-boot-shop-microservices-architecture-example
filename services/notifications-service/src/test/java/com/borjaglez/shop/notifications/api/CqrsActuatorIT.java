package com.borjaglez.shop.notifications.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import com.borjaglez.shop.notifications.TestContainers;

/** The cqrs actuator endpoint of the Boot 3 starter. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "shop.notifications.sweep-interval=1h")
@Import(TestContainers.class)
class CqrsActuatorIT {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};

  @LocalServerPort int port;
  RestClient http;

  @BeforeEach
  void client() {
    http =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();
  }

  private ResponseEntity<Map<String, Object>> get(String uri) {
    return http.get().uri(uri).retrieve().toEntity(JSON);
  }

  @Test
  @SuppressWarnings("unchecked")
  void theEndpointDescribesTheHandlersAndMiddleware() {
    ResponseEntity<Map<String, Object>> response = get("/actuator/cqrs");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> counts = (Map<String, Object>) response.getBody().get("counts");
    assertThat((Integer) counts.get("commands")).isPositive();
    assertThat((Integer) counts.get("queries")).isPositive();
    assertThat((List<Map<String, Object>>) response.getBody().get("middleware"))
        .anyMatch(middleware -> Boolean.TRUE.equals(middleware.get("observability")));
  }

  @Test
  void anUnknownKindOrSectionIsABadRequest() {
    assertThat(get("/actuator/cqrs/handlers/sagas").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(get("/actuator/cqrs/listeners/event").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void theInfoEndpointSummarisesTheBuses() {
    assertThat(get("/actuator/info").getBody()).containsKey("cqrs");
  }
}
