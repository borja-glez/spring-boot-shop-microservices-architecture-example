package com.borjaglez.shop.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.borjaglez.shop.system.Checkouts.Placed;

import tools.jackson.databind.JsonNode;

/**
 * Each fault of the chaos panel, and how the platform absorbs it. Needs the services started with
 * {@code SHOP_CHAOS_ENABLED=true} (Compose and the local Kubernetes overlays).
 */
@Tag("system")
class ChaosJourneyTest {

  private static final ShopClient shop = ShopClient.fromSystemProperties();
  private static final Checkouts checkouts = new Checkouts(shop);

  private static UUID product;

  @BeforeAll
  static void chaosAndAProduct() {
    assumeTrue(shop.chaosEnabled(), "chaos is off in this deployment");
    shop.calm();
    product = checkouts.productWithStock(Checkouts.unique("CHS"), new BigDecimal("3.00"), 50);
  }

  @AfterEach
  void calm() {
    shop.calm();
  }

  private static Placed place() {
    String customer = Checkouts.unique("caos");
    return new Placed(customer, shop.placeOrder(customer, product, 1), 1);
  }

  @Test
  void whenEveryCardIsDeclinedTheOrderIsRejectedAndTheStockReleased() {
    shop.chaos("payments", "payments.decline-all", 1);

    Placed order = place();

    assertThat(checkouts.assertConsistent(order)).isEqualTo("REJECTED");
  }

  @Test
  void whilePaymentsFailTheSagaRetriesAndConfirmsOnceTheyAreBack() {
    shop.chaos("payments", "AuthorizePayment.fail", 1);
    Placed order = place();

    JsonNode retrying =
        await()
            .atMost(Duration.ofSeconds(30))
            .until(
                () -> shop.saga(order.customer(), order.orderId()),
                saga -> saga.get("attempts").asInt() >= 2);
    assertThat(retrying.get("state").asString()).isEqualTo("RUNNING");
    shop.chaos("payments", "AuthorizePayment.fail", 0);

    assertThat(checkouts.assertConsistent(order)).isEqualTo("CONFIRMED");
  }

  @Test
  void slowPaymentsTimeOutButNobodyPaysTwice() {
    shop.chaos("payments", "AuthorizePayment.delay-ms", 7000);
    Placed order = place();

    await()
        .atMost(Duration.ofSeconds(40))
        .until(
            () -> shop.saga(order.customer(), order.orderId()),
            saga -> String.valueOf(saga.get("lastError")).contains("reply timeout"));
    shop.chaos("payments", "AuthorizePayment.delay-ms", 0);

    assertThat(checkouts.assertConsistent(order)).isEqualTo("CONFIRMED");
    assertThat(shop.payments(order.orderId())).hasSize(1);
  }

  @Test
  void withTheRelayPausedTheSagaFinishesAndTheReportsCatchUpLater() {
    shop.chaos("orders", "relay.paused", 1);
    Placed order = place();

    JsonNode saga = checkouts.settle(order);
    assertThat(saga.get("mode").asString()).isEqualTo("CHECKOUT");
    assertThat(shop.reportedOrders(order.customer())).isZero();
    shop.chaos("orders", "relay.paused", 0);

    await()
        .atMost(Checkouts.EVENT_PATIENCE)
        .until(() -> shop.reportedOrders(order.customer()) == 1);
    assertThat(checkouts.assertConsistent(order)).isEqualTo("CONFIRMED");
  }

  @Test
  void duplicatedEventsAreCountedOnce() {
    for (String service : ShopClient.CHAOS_SERVICES) {
      shop.chaos(service, "relay.duplicate", 1);
    }
    Placed order = place();

    assertThat(checkouts.assertConsistent(order)).isEqualTo("CONFIRMED");
    await()
        .atMost(Checkouts.EVENT_PATIENCE)
        .until(() -> shop.reportedOrders(order.customer()) >= 1);
    // The duplicates keep coming for a while: the count must stay at one.
    await()
        .during(java.time.Duration.ofSeconds(5))
        .atMost(Checkouts.EVENT_PATIENCE.plusSeconds(5))
        .until(() -> shop.reportedOrders(order.customer()) == 1);
  }
}
