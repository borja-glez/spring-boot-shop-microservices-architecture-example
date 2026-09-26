package com.borjaglez.shop.orders.api;

import static com.borjaglez.shop.orders.OrdersTestSupport.published;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import com.borjaglez.shop.orders.application.projection.CatalogProductProjector;
import com.borjaglez.shop.orders.checkout.CheckoutDriver;
import com.borjaglez.shop.orders.checkout.FakeCheckout;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;

/** The orders API end to end: HTTP, buses, event store, Kafka and the read model. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "shop.checkout.enabled=false",
      "shop.checkout.initial-backoff=0s",
      "shop.checkout.max-backoff=0s"
    })
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  FakeCheckout.class,
  CheckoutDriver.class
})
class OrdersApiIT {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST =
      new ParameterizedTypeReference<>() {};

  @LocalServerPort int port;
  @Autowired CatalogProductProjector catalog;
  @Autowired CheckoutDriver checkout;
  RestClient http;
  String customer;

  @BeforeEach
  void client() {
    http =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();
    customer = "cliente-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private ResponseEntity<Map<String, Object>> get(String uri, Object... args) {
    return http.get().uri(uri, args).header("X-Shop-User", customer).retrieve().toEntity(JSON);
  }

  private ResponseEntity<Map<String, Object>> placeOrder(UUID productId, int quantity) {
    return http.post()
        .uri("/api/orders")
        .header("X-Shop-User", customer)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("items", List.of(Map.of("productId", productId, "quantity", quantity))))
        .retrieve()
        .toEntity(JSON);
  }

  private UUID product(String price) {
    UUID id = UUID.randomUUID();
    catalog.on(published(id, "API-" + id.toString().substring(0, 4), price));
    return id;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> content(ResponseEntity<Map<String, Object>> response) {
    return (List<Map<String, Object>>) response.getBody().get("content");
  }

  @Test
  void aPlacedOrderShowsUpInMyFilteredOrders() {
    var created = placeOrder(product("3.20"), 5);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String orderId = (String) created.getBody().get("id");

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              var response = get("/api/orders?filter=status:eq:PLACED&sort=total,desc");
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(content(response))
                  .singleElement()
                  .satisfies(
                      order -> {
                        assertThat(order.get("orderId")).isEqualTo(orderId);
                        assertThat(order.get("total")).isEqualTo(16.0);
                      });
            });
  }

  @Test
  void theCustomerCannotBeChosenThroughTheFilters() {
    var response = get("/api/orders?filter=customerId:eq:cliente-mateo");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("code", "invalid-filter");
  }

  @Test
  void unavailableProductsAreUnprocessable() {
    var response = placeOrder(UUID.randomUUID(), 1);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody()).containsEntry("code", "product-unavailable");
  }

  @Test
  void theHistoryAndTheExplorerShowTheStoredEvents() {
    String orderId = (String) placeOrder(product("1.00"), 1).getBody().get("id");

    var history =
        http.get()
            .uri("/api/orders/{id}/history", orderId)
            .header("X-Shop-User", customer)
            .retrieve()
            .toEntity(JSON_LIST);
    assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(history.getBody())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.get("eventType"))
                  .isEqualTo("shop.orders.1.event.order.order-placed");
              assertThat(((Map<?, ?>) entry.get("event")).get("orderId")).isEqualTo(orderId);
            });

    var events = get("/api/orders/events?filter=streamId:eq:{id}", orderId);
    assertThat(events.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(content(events))
        .singleElement()
        .satisfies(
            row -> assertThat(((Map<?, ?>) row.get("payload")).get("orderId")).isEqualTo(orderId));
  }

  @Test
  void anotherCustomersOrderIsNotFound() {
    String orderId = (String) placeOrder(product("1.00"), 1).getBody().get("id");
    customer = "cliente-intruso";

    var response = get("/api/orders/{id}/history", orderId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsEntry("code", "order-not-found");
  }

  @Test
  void theCheckoutShowsEachStepAndCancellingNeedsAConfirmedOrder() {
    String orderId = (String) placeOrder(product("2.00"), 1).getBody().get("id");

    var early =
        http.post()
            .uri("/api/orders/{id}/cancel", orderId)
            .header("X-Shop-User", customer)
            .retrieve()
            .toEntity(JSON);
    assertThat(early.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(early.getBody()).containsEntry("code", "checkout-in-progress");

    checkout.finish(UUID.fromString(orderId));
    var saga = get("/api/orders/{id}/checkout", orderId);

    assertThat(saga.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(saga.getBody()).containsEntry("state", "COMPLETED").containsEntry("step", "DONE");
    assertThat((List<?>) saga.getBody().get("steps")).hasSize(3);
    assertThat(
            http.post()
                .uri("/api/orders/{id}/cancel", orderId)
                .header("X-Shop-User", customer)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    customer = "cliente-intruso";
    assertThat(get("/api/orders/{id}/checkout", orderId).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
