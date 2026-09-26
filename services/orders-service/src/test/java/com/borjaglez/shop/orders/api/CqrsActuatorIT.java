package com.borjaglez.shop.orders.api;

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

import com.borjaglez.shop.orders.checkout.CheckoutDriver;
import com.borjaglez.shop.orders.checkout.FakeCheckout;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;

/** The cqrs actuator endpoint and info contributor of the library, in a real service. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "shop.checkout.enabled=false")
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  FakeCheckout.class,
  CheckoutDriver.class
})
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
    Map<String, Object> body = response.getBody();
    Map<String, Object> counts = (Map<String, Object>) body.get("counts");
    assertThat((Integer) counts.get("commands")).isPositive();
    assertThat((Integer) counts.get("events")).isPositive();
    assertThat((Integer) counts.get("queries")).isPositive();
    assertThat((List<Map<String, Object>>) body.get("handlers"))
        .extracting(handler -> handler.get("messageType"))
        .contains("com.borjaglez.shop.orders.application.command.PlaceOrderCommand");
    // Tracing and metrics middleware exist only when they are ordered after Boot's registries.
    assertThat((List<Map<String, Object>>) body.get("middleware"))
        .anyMatch(middleware -> Boolean.TRUE.equals(middleware.get("observability")));
  }

  @Test
  void handlersCanBeListedByKind() {
    ResponseEntity<List<Map<String, Object>>> response =
        http.get()
            .uri("/actuator/cqrs/handlers/event")
            .retrieve()
            .toEntity(new ParameterizedTypeReference<>() {});

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotEmpty().allMatch(h -> "event".equals(h.get("kind")));
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
