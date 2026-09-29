package com.borjaglez.shop.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.borjaglez.shop.system.Checkouts.Placed;

import tools.jackson.databind.JsonNode;

/**
 * The reads that cross services over RabbitMQ, as the pages use them: the product page asks
 * inventory for the stock, the cart quote asks it too, and the order page asks notifications
 * (Spring Boot 3) for the notices of the order.
 */
@Tag("system")
class LiveReadsJourneyTest {

  private static final ShopClient shop = ShopClient.fromSystemProperties();
  private static final Checkouts checkouts = new Checkouts(shop);

  @Test
  void theProductPageAndTheCartSeeTheStockOfTheInventory() {
    String sku = Checkouts.unique("LIVE").toUpperCase(Locale.ROOT);
    UUID product = checkouts.productWithStock(sku, new BigDecimal("6.00"), 4);

    JsonNode availability = shop.productPage(sku).get("availability");
    assertThat(availability.get("status").asString()).isEqualTo("LOW_STOCK");
    assertThat(availability.get("units").asInt()).isEqualTo(4);

    String customer = Checkouts.unique("cliente");
    JsonNode tooMany = shop.quote(customer, product, 6);
    assertThat(tooMany.get("stockChecked").asBoolean()).isTrue();
    assertThat(tooMany.get("orderable").asBoolean()).isFalse();
    assertThat(tooMany.get("lines").get(0).get("problem").asString()).isEqualTo("NOT_ENOUGH_STOCK");

    JsonNode enough = shop.quote(customer, product, 3);
    assertThat(enough.get("orderable").asBoolean()).isTrue();
    assertThat(enough.get("total").decimalValue()).isEqualByComparingTo("18.00");

    Placed order = new Placed(customer, shop.placeOrder(customer, product, 3), 3);
    assertThat(checkouts.assertConsistent(order)).isEqualTo("CONFIRMED");
    assertThat(shop.productPage(sku).get("availability").get("units").asInt()).isEqualTo(1);
  }

  @Test
  void theOrderPageShowsTheNoticesOfTheBoot3Service() {
    UUID product = checkouts.productWithStock(Checkouts.unique("NTC"), new BigDecimal("2.50"), 5);
    String customer = Checkouts.unique("cliente");
    Placed order = new Placed(customer, shop.placeOrder(customer, product, 1), 1);
    assertThat(checkouts.assertConsistent(order)).isEqualTo("CONFIRMED");

    JsonNode notices =
        await("notices of " + order.orderId())
            .atMost(Checkouts.EVENT_PATIENCE)
            .pollInterval(Duration.ofMillis(500))
            .until(
                () -> shop.notices(customer, order.orderId()),
                n -> n.get("available").asBoolean() && !n.get("notices").isEmpty());
    JsonNode notice = notices.get("notices").get(0);
    assertThat(notice.get("kind").asString()).isEqualTo("ORDER_CONFIRMED");
    assertThat(notice.get("body").asString()).contains("€2.50");
    assertThat(notice.get("sentAt").asString()).endsWith("Z");
  }
}
