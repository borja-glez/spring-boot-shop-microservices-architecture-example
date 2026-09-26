package com.borjaglez.shop.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.borjaglez.shop.system.Checkouts.Placed;

/**
 * Many customers want the last units at once. Reservations are all-or-nothing and serialised per
 * product, so exactly as many orders as there are units go through, the rest are rejected for lack
 * of stock, nobody pays for a rejected order and the warehouse never goes below zero.
 */
@Tag("system")
class ScarceStockStressTest {

  private static final int UNITS = 5;
  private static final int CUSTOMERS = 40;

  private final ShopClient shop = ShopClient.fromSystemProperties();
  private final Checkouts checkouts = new Checkouts(shop);

  static List<Placed> placeAtOnce(ShopClient shop, UUID product, int customers, int quantity)
      throws Exception {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Placed>> futures =
          java.util.stream.IntStream.range(0, customers)
              .mapToObj(
                  i ->
                      executor.submit(
                          () -> {
                            String customer = Checkouts.unique("rafaga");
                            return new Placed(
                                customer, shop.placeOrder(customer, product, quantity), quantity);
                          }))
              .toList();
      List<Placed> placed = new java.util.ArrayList<>();
      for (Future<Placed> future : futures) {
        placed.add(future.get());
      }
      return placed;
    }
  }

  static Map<String, Long> outcomes(Checkouts checkouts, List<Placed> placed) throws Exception {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<String>> futures =
          placed.stream().map(p -> executor.submit(() -> checkouts.assertConsistent(p))).toList();
      List<String> results = new java.util.ArrayList<>();
      for (Future<String> future : futures) {
        results.add(future.get());
      }
      return results.stream()
          .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
  }

  @Test
  void onlyAsManyOrdersAsUnitsGoThrough() throws Exception {
    String sku = Checkouts.unique("SCARCE").toUpperCase(java.util.Locale.ROOT);
    UUID product = checkouts.productWithStock(sku, new BigDecimal("1.99"), UNITS);

    List<Placed> placed = placeAtOnce(shop, product, CUSTOMERS, 1);
    Map<String, Long> outcomes = outcomes(checkouts, placed);

    assertThat(outcomes).containsEntry("CONFIRMED", (long) UNITS);
    assertThat(outcomes).containsEntry("REJECTED", (long) (CUSTOMERS - UNITS));
    for (Placed order : placed) {
      String reason =
          shop.order(order.customer(), order.orderId()).path("rejectionReason").asString("");
      assertThat(reason).isIn("", "out-of-stock");
    }
    checkouts.assertStock(sku, UNITS, UNITS);
  }

  @Test
  void cancellingEveryConfirmedOrderGivesAllTheStockBack() throws Exception {
    String sku = Checkouts.unique("SCARCE-CXL").toUpperCase(java.util.Locale.ROOT);
    UUID product = checkouts.productWithStock(sku, new BigDecimal("2.49"), 20);
    List<Placed> placed = placeAtOnce(shop, product, 10, 2);
    assertThat(outcomes(checkouts, placed)).containsEntry("CONFIRMED", 10L);

    // Every customer cancels twice at the same time: one wins, the other is a conflict or no-op.
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Integer>> answers =
          placed.stream()
              .flatMap(p -> java.util.stream.Stream.of(p, p))
              .map(p -> executor.submit(() -> shop.cancel(p.customer(), p.orderId()).status()))
              .toList();
      for (Future<Integer> answer : answers) {
        assertThat(answer.get()).isIn(204, 409);
      }
    }

    assertThat(outcomes(checkouts, placed)).containsEntry("CANCELLED", 10L);
    for (Placed order : placed) {
      assertThat(shop.payments(order.orderId())).hasSize(1);
    }
    checkouts.assertStock(sku, 20, 0);
  }
}
