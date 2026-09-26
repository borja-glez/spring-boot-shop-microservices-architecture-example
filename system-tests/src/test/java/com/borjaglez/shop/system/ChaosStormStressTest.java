package com.borjaglez.shop.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.borjaglez.shop.system.Checkouts.Placed;

/**
 * A burst of orders while several faults hit at once: duplicated events everywhere, slow
 * reservations, failing payments that come and go, a relay that stops for a while. Once the faults
 * are gone, every order must end consistent in every service and the reports must count each
 * confirmed order exactly once.
 */
@Tag("system")
class ChaosStormStressTest {

  private static final int CUSTOMERS = 25;

  private final ShopClient shop = ShopClient.fromSystemProperties();
  private final Checkouts checkouts = new Checkouts(shop);

  @AfterEach
  void calm() {
    if (shop.chaosEnabled()) {
      shop.calm();
    }
  }

  @Test
  void everyOrderEndsConsistentAfterTheStorm() throws Exception {
    assumeTrue(shop.chaosEnabled(), "chaos is off in this deployment");
    shop.calm();
    String sku = Checkouts.unique("STORM").toUpperCase(java.util.Locale.ROOT);
    UUID product = checkouts.productWithStock(sku, new BigDecimal("5.00"), CUSTOMERS * 2);

    for (String service : ShopClient.CHAOS_SERVICES) {
      shop.chaos(service, "relay.duplicate", 1);
    }
    shop.chaos("inventory", "ReserveStock.delay-ms", 1500);
    shop.chaos("payments", "AuthorizePayment.fail", 1);
    shop.chaos("orders", "relay.paused", 1);

    List<Placed> placed = ScarceStockStressTest.placeAtOnce(shop, product, CUSTOMERS, 1);
    // Lift the payment fault only once it has hit every checkout.
    for (Placed order : placed) {
      await().atMost(Checkouts.EVENT_PATIENCE).until(() -> paymentWasRetried(order));
    }
    shop.chaos("payments", "AuthorizePayment.fail", 0);
    shop.chaos("orders", "relay.paused", 0);
    // Keep duplicating events until the reports have seen every order.
    for (Placed order : placed) {
      await()
          .atMost(Checkouts.EVENT_PATIENCE)
          .until(() -> shop.reportedOrders(order.customer()) >= 1);
    }
    shop.calm();

    Map<String, Long> outcomes = ScarceStockStressTest.outcomes(checkouts, placed);
    long confirmed = outcomes.getOrDefault("CONFIRMED", 0L);
    assertThat(confirmed + outcomes.getOrDefault("REJECTED", 0L)).isEqualTo(CUSTOMERS);
    // Payments were down for a few seconds only, well inside the saga's retries.
    assertThat(confirmed).isEqualTo(CUSTOMERS);
    checkouts.assertStock(sku, CUSTOMERS * 2, (int) confirmed);
    for (Placed order : placed) {
      await()
          .atMost(Checkouts.EVENT_PATIENCE)
          .untilAsserted(() -> assertThat(shop.reportedOrders(order.customer())).isEqualTo(1));
    }
  }

  private boolean paymentWasRetried(Placed order) {
    for (var step : shop.saga(order.customer(), order.orderId()).get("steps")) {
      if ("AUTHORIZE_PAYMENT".equals(step.get("step").asString())
          && "RETRYING".equals(step.get("outcome").asString())) {
        return true;
      }
    }
    return false;
  }
}
