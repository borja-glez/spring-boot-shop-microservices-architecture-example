package com.borjaglez.shop.orders.api;

import static com.borjaglez.shop.orders.OrdersTestSupport.published;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.OffsetDateTime;
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

import com.borjaglez.shop.contracts.notifications.OrderNotice;
import com.borjaglez.shop.orders.FakeRemoteReads;
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
  FakeRemoteReads.class,
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
  @Autowired FakeRemoteReads remote;
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

  private ResponseEntity<Map<String, Object>> quote(Map<UUID, Integer> items) {
    return http.post()
        .uri("/api/orders/quote")
        .header("X-Shop-User", customer)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "items",
                items.entrySet().stream()
                    .map(e -> Map.of("productId", e.getKey(), "quantity", e.getValue()))
                    .toList()))
        .retrieve()
        .toEntity(JSON);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> lines(ResponseEntity<Map<String, Object>> response) {
    return (List<Map<String, Object>>) response.getBody().get("lines");
  }

  @Test
  void theQuotePricesTheCartAndChecksItsStock() {
    UUID tea = product("4.50");
    UUID honey = product("7.00");
    UUID gone = UUID.randomUUID();
    remote.stock(tea, 10);
    remote.stock(honey, 1);

    var response = quote(Map.of(tea, 2, honey, 3, gone, 1));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("total", 30.0)
        .containsEntry("currency", "EUR")
        .containsEntry("stockChecked", true)
        .containsEntry("orderable", false);
    assertThat(lines(response))
        .extracting(l -> l.get("productId"), l -> l.get("available"), l -> l.get("problem"))
        .containsExactlyInAnyOrder(
            tuple(tea.toString(), 10, null),
            tuple(honey.toString(), 1, "NOT_ENOUGH_STOCK"),
            tuple(gone.toString(), null, "NOT_FOR_SALE"));
  }

  @Test
  void withoutTheInventoryTheQuoteOnlyChecksPrices() {
    UUID coffee = product("9.90");
    remote.inventorySilentAbout(coffee);

    var response = quote(Map.of(coffee, 1));

    assertThat(response.getBody())
        .containsEntry("stockChecked", false)
        .containsEntry("orderable", true)
        .containsEntry("total", 9.9);
    assertThat(lines(response))
        .singleElement()
        .satisfies(l -> assertThat(l.get("available")).isNull());
  }

  @Test
  void anEmptyCartCannotBeQuoted() {
    assertThat(quote(Map.of()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private UUID viewedOrder() {
    UUID orderId = UUID.fromString((String) placeOrder(product("1.00"), 1).getBody().get("id"));
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> get("/api/orders/{id}", orderId).getStatusCode().is2xxSuccessful());
    return orderId;
  }

  @Test
  @SuppressWarnings("unchecked")
  void theOrderShowsTheNoticesTheCustomerReceived() {
    UUID orderId = viewedOrder();
    remote.notices(
        orderId,
        new OrderNotice(
            "ORDER_CONFIRMED",
            "Order confirmed",
            "Your order is on its way",
            OffsetDateTime.parse("2026-09-29T10:00:00Z"),
            false));

    var response = get("/api/orders/{id}/notices", orderId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("available", true);
    assertThat((List<Map<String, Object>>) response.getBody().get("notices"))
        .singleElement()
        .satisfies(
            n -> {
              assertThat(n.get("kind")).isEqualTo("ORDER_CONFIRMED");
              assertThat(n.get("sentAt")).isEqualTo("2026-09-29T10:00:00Z");
            });
  }

  @Test
  void withoutNotificationsTheOrderSaysSo() {
    UUID orderId = viewedOrder();
    remote.notificationsSilentAbout(orderId);

    var response = get("/api/orders/{id}/notices", orderId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("available", false);
  }

  @Test
  void anotherCustomersNoticesAreNotFound() {
    UUID orderId = viewedOrder();
    customer = "cliente-intruso";

    var response = get("/api/orders/{id}/notices", orderId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsEntry("code", "order-not-found");
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
