package com.borjaglez.shop.system;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The public API of the platform, as the shop uses it: every call goes through the gateway with the
 * {@code X-Shop-User} header. Answers are plain JSON trees, so the tests read the API as a client
 * does, without sharing classes with the services.
 */
public final class ShopClient {

  /** An HTTP answer: status and body ({@code null} when empty). */
  public record Response(int status, JsonNode body) {

    public Response expect(int expected) {
      if (status != expected) {
        throw new AssertionError("Expected HTTP " + expected + " but got " + status + ": " + body);
      }
      return this;
    }
  }

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final String baseUrl;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public ShopClient(String baseUrl) {
    this.baseUrl = baseUrl.replaceAll("/+$", "");
  }

  /** The platform under test: {@code -Dshop.baseUrl}, the gateway on localhost by default. */
  public static ShopClient fromSystemProperties() {
    return new ShopClient(System.getProperty("shop.baseUrl", "http://localhost:8080"));
  }

  public String baseUrl() {
    return baseUrl;
  }

  public Response call(String method, String path, Object body, String user) {
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create(baseUrl + path))
              .timeout(Duration.ofSeconds(30))
              .header("X-Shop-User", user)
              .header("Accept", "application/json");
      if (body == null) {
        request.method(method, HttpRequest.BodyPublishers.noBody());
      } else {
        request
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
      }
      HttpResponse<String> response =
          http.send(request.build(), HttpResponse.BodyHandlers.ofString());
      String text = response.body();
      return new Response(
          response.statusCode(), text == null || text.isBlank() ? null : JSON.readTree(text));
    } catch (IOException e) {
      throw new IllegalStateException(method + " " + path + " failed: " + e, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(method + " " + path + " interrupted", e);
    }
  }

  public JsonNode get(String path, String user) {
    return call("GET", path, null, user).expect(200).body();
  }

  static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  // --- catalog and inventory

  /** Creates and publishes a product; inventory starts it with 25 units once the event arrives. */
  public UUID newProduct(String seller, String sku, BigDecimal price) {
    JsonNode created =
        call(
                "POST",
                "/api/catalog/products",
                Map.of(
                    "sku",
                    sku,
                    "name",
                    "Producto de prueba " + sku,
                    "price",
                    price,
                    "currency",
                    "EUR",
                    "categories",
                    List.of("hogar")),
                seller)
            .expect(201)
            .body();
    UUID id = UUID.fromString(created.get("id").asString());
    call("POST", "/api/catalog/products/" + id + "/publish", null, seller).expect(204);
    return id;
  }

  /** The stock of a product, or {@code null} while inventory has not heard of it. */
  public JsonNode stock(String sku) {
    JsonNode page = get("/api/inventory/stock?filter=" + encode("sku:eq:" + sku), "backoffice");
    return page.get("content").isEmpty() ? null : page.get("content").get(0);
  }

  public Response countStock(UUID productId, int onHand) {
    return call("PUT", "/api/inventory/stock/" + productId, Map.of("onHand", onHand), "backoffice");
  }

  public JsonNode reservation(UUID orderId) {
    JsonNode page =
        get("/api/inventory/reservations?filter=" + encode("orderId:eq:" + orderId), "backoffice");
    return page.get("content").isEmpty() ? null : page.get("content").get(0);
  }

  // --- orders

  public Response place(String customer, UUID productId, int quantity) {
    return call(
        "POST",
        "/api/orders",
        Map.of("items", List.of(Map.of("productId", productId, "quantity", quantity))),
        customer);
  }

  public UUID placeOrder(String customer, UUID productId, int quantity) {
    return UUID.fromString(
        place(customer, productId, quantity).expect(201).body().get("id").asString());
  }

  public Response cancel(String customer, UUID orderId) {
    return call(
        "POST", "/api/orders/" + orderId + "/cancel", Map.of("reason", "system test"), customer);
  }

  public JsonNode order(String customer, UUID orderId) {
    return get("/api/orders/" + orderId, customer);
  }

  public JsonNode saga(String customer, UUID orderId) {
    return get("/api/orders/" + orderId + "/checkout", customer);
  }

  // --- payments and reports

  public List<JsonNode> payments(UUID orderId) {
    JsonNode page = get("/api/payments?filter=" + encode("orderId:eq:" + orderId), "backoffice");
    List<JsonNode> payments = new ArrayList<>();
    page.get("content").forEach(payments::add);
    return payments;
  }

  /** Orders and money the reports attribute to a customer (0 when absent). */
  public long reportedOrders(String customer) {
    long orders = 0;
    for (JsonNode row : get("/api/reporting/customers", "backoffice")) {
      if (row.get("customerId").asString().equals(customer)) {
        orders += row.get("orders").asLong();
      }
    }
    return orders;
  }

  // --- chaos

  public static final List<String> CHAOS_SERVICES =
      List.of("payments", "inventory", "orders", "catalog");

  public void chaos(String service, String fault, long value) {
    call("PUT", "/api/" + service + "/chaos/" + fault, Map.of("value", value), "backoffice")
        .expect(200);
  }

  /** Turns every fault off; a service without chaos answers 404, which is fine too. */
  public void calm() {
    for (String service : CHAOS_SERVICES) {
      int status = call("DELETE", "/api/" + service + "/chaos", null, "backoffice").status();
      if (status != 204 && status != 404) {
        throw new AssertionError("Could not calm " + service + ": HTTP " + status);
      }
    }
  }

  public boolean chaosEnabled() {
    return call("GET", "/api/payments/chaos", null, "backoffice").status() == 200;
  }
}
