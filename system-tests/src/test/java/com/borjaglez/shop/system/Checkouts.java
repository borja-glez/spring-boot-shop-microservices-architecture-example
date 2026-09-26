package com.borjaglez.shop.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** Waits for the asynchronous parts of the platform, and checks what every order must satisfy. */
public final class Checkouts {

  /** Longest a checkout may take, retries and compensations included. */
  public static final Duration SAGA_PATIENCE = Duration.ofMinutes(4);

  /** Longest events may take to reach the read models and the reports. */
  public static final Duration EVENT_PATIENCE = Duration.ofSeconds(60);

  private final ShopClient shop;

  public Checkouts(ShopClient shop) {
    this.shop = shop;
  }

  /** An order placed by a customer, as the tests keep track of it. */
  public record Placed(String customer, UUID orderId, int quantity) {}

  /** A fresh product with {@code units} in the warehouse, so tests never share stock. */
  public UUID productWithStock(String requestedSku, BigDecimal price, int units) {
    // The catalog keeps SKUs in upper case.
    String sku = requestedSku.toUpperCase(java.util.Locale.ROOT);
    UUID productId = shop.newProduct("seller-ana", sku, price);
    await("inventory learns about " + sku)
        .atMost(EVENT_PATIENCE)
        .pollInterval(Duration.ofMillis(500))
        .until(() -> shop.stock(sku) != null);
    shop.countStock(productId, units).expect(204);
    return productId;
  }

  /** Waits until the saga of the order is no longer running, and returns it. */
  public JsonNode settle(Placed placed) {
    return await("checkout of " + placed.orderId())
        .atMost(SAGA_PATIENCE)
        .pollInterval(Duration.ofMillis(500))
        .until(
            () -> shop.saga(placed.customer(), placed.orderId()),
            saga -> !"RUNNING".equals(saga.get("state").asString()));
  }

  /** Waits until the order's read model shows the status (it follows the events). */
  public JsonNode orderReaches(Placed placed, String status) {
    return await("order " + placed.orderId() + " " + status)
        .atMost(EVENT_PATIENCE)
        .pollInterval(Duration.ofMillis(500))
        .until(
            () -> shop.order(placed.customer(), placed.orderId()),
            order -> status.equals(order.get("status").asString()));
  }

  /**
   * What a settled order must look like in every service. A completed checkout is confirmed, paid
   * once and holds its stock; a rejected one holds nothing and nobody paid for it; a cancelled one
   * was refunded once and gave its stock back.
   *
   * @return the final status of the order
   */
  public String assertConsistent(Placed placed) {
    JsonNode saga = settle(placed);
    assertThat(saga.get("state").asString())
        .as("saga of %s", placed.orderId())
        .isEqualTo("COMPLETED");
    String mode = saga.get("mode").asString();
    List<JsonNode> payments = shop.payments(placed.orderId());
    assertThat(payments).as("payments of %s", placed.orderId()).hasSizeLessThanOrEqualTo(1);
    String payment = payments.isEmpty() ? "NONE" : payments.getFirst().get("status").asString();
    JsonNode reservation = shop.reservation(placed.orderId());
    String stock = reservation == null ? "NONE" : reservation.get("status").asString();
    switch (mode) {
      case "CHECKOUT" -> {
        orderReaches(placed, "CONFIRMED");
        assertThat(payment).as("payment of confirmed %s", placed.orderId()).isEqualTo("AUTHORIZED");
        assertThat(stock).as("stock of confirmed %s", placed.orderId()).isEqualTo("RESERVED");
        return "CONFIRMED";
      }
      case "REJECTING" -> {
        orderReaches(placed, "REJECTED");
        assertThat(payment)
            .as("payment of rejected %s", placed.orderId())
            .isIn("NONE", "DECLINED", "REFUNDED");
        assertThat(stock).as("stock of rejected %s", placed.orderId()).isIn("NONE", "RELEASED");
        return "REJECTED";
      }
      case "CANCELLING" -> {
        orderReaches(placed, "CANCELLED");
        assertThat(payment).as("payment of cancelled %s", placed.orderId()).isEqualTo("REFUNDED");
        assertThat(stock).as("stock of cancelled %s", placed.orderId()).isEqualTo("RELEASED");
        return "CANCELLED";
      }
      default -> throw new AssertionError("Unknown saga mode " + mode + " for " + placed);
    }
  }

  /**
   * The warehouse never promises more than it has, and holds exactly what confirmed orders took.
   */
  public void assertStock(String sku, int onHand, int reserved) {
    await("stock of " + sku)
        .atMost(EVENT_PATIENCE)
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              JsonNode stock = shop.stock(sku);
              assertThat(stock.get("onHand").asInt()).as("on hand").isEqualTo(onHand);
              assertThat(stock.get("reserved").asInt()).as("reserved").isEqualTo(reserved);
              assertThat(stock.get("available").asInt())
                  .as("available")
                  .isEqualTo(onHand - reserved)
                  .isNotNegative();
            });
  }

  public static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }
}
