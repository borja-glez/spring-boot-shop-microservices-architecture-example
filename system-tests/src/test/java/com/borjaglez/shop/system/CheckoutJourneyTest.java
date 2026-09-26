package com.borjaglez.shop.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.borjaglez.shop.system.Checkouts.Placed;

import tools.jackson.databind.JsonNode;

/** The checkout as a customer lives it, through the gateway of a running platform. */
@Tag("system")
class CheckoutJourneyTest {

  private static final ShopClient shop = ShopClient.fromSystemProperties();
  private static final Checkouts checkouts = new Checkouts(shop);

  private static String sku;
  private static UUID cheap;
  private static UUID pricey;

  @BeforeAll
  static void products() {
    sku = Checkouts.unique("JRN");
    cheap = checkouts.productWithStock(sku, new BigDecimal("4.50"), 10);
    pricey = checkouts.productWithStock(Checkouts.unique("JRN-LUX"), new BigDecimal("950.00"), 10);
  }

  private static Placed place(UUID product, int quantity) {
    String customer = Checkouts.unique("cliente");
    return new Placed(customer, shop.placeOrder(customer, product, quantity), quantity);
  }

  @Test
  void aPaidOrderIsConfirmedAndHoldsItsStock() {
    Placed order = place(cheap, 2);

    assertThat(checkouts.assertConsistent(order)).isEqualTo("CONFIRMED");
  }

  @Test
  void anOrderAboveTheCardLimitIsRejectedAndReleasesItsStock() {
    Placed order = place(pricey, 1);

    assertThat(checkouts.assertConsistent(order)).isEqualTo("REJECTED");
    assertThat(shop.order(order.customer(), order.orderId()).get("rejectionReason").asString())
        .isEqualTo("card-limit-exceeded");
  }

  @Test
  void anOrderForMoreThanTheStockIsRejectedWithoutCharging() {
    Placed order = place(cheap, 11);

    assertThat(checkouts.assertConsistent(order)).isEqualTo("REJECTED");
    assertThat(shop.order(order.customer(), order.orderId()).get("rejectionReason").asString())
        .isEqualTo("out-of-stock");
    assertThat(shop.payments(order.orderId())).isEmpty();
  }

  @Test
  void aCancelledOrderIsRefundedAndGivesItsStockBack() {
    Placed order = place(cheap, 1);
    checkouts.assertConsistent(order);

    shop.cancel(order.customer(), order.orderId()).expect(204);

    assertThat(checkouts.assertConsistent(order)).isEqualTo("CANCELLED");
  }

  @Test
  void anOrderCannotBeCancelledWhileItsCheckoutRuns() {
    Placed order = place(cheap, 1);

    ShopClient.Response early = shop.cancel(order.customer(), order.orderId());

    // The saga may already have finished on a fast platform; then the cancellation is accepted.
    if (early.status() == 409) {
      JsonNode problem = early.body();
      assertThat(problem.get("code").asString()).isEqualTo("checkout-in-progress");
    } else {
      early.expect(204);
    }
    checkouts.assertConsistent(order);
  }

  @Test
  void aCustomerCannotSeeSomeoneElsesOrder() {
    Placed order = place(cheap, 1);

    assertThat(shop.call("GET", "/api/orders/" + order.orderId(), null, "otro-cliente").status())
        .isEqualTo(404);
    checkouts.assertConsistent(order);
  }
}
