package com.borjaglez.shop.orders;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.borjaglez.shop.contracts.notifications.OrderNotice;
import com.borjaglez.shop.orders.application.query.OrderNoticesGateway;
import com.borjaglez.shop.orders.application.query.StockLevelsGateway;

/**
 * Inventory and notifications that answer as each test tells them, per product and per order, so
 * tests sharing a context do not see each other's data.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeRemoteReads {

  private final Map<UUID, Integer> stock = new ConcurrentHashMap<>();
  private final Set<UUID> silentProducts = ConcurrentHashMap.newKeySet();
  private final Map<UUID, List<OrderNotice>> notices = new ConcurrentHashMap<>();
  private final Set<UUID> silentOrders = ConcurrentHashMap.newKeySet();

  public void stock(UUID productId, int available) {
    stock.put(productId, available);
  }

  /** The inventory does not answer when asked about this product. */
  public void inventorySilentAbout(UUID productId) {
    silentProducts.add(productId);
  }

  public void notices(UUID orderId, OrderNotice... sent) {
    notices.put(orderId, List.of(sent));
  }

  /** The notifications service does not answer when asked about this order. */
  public void notificationsSilentAbout(UUID orderId) {
    silentOrders.add(orderId);
  }

  @Bean
  @Primary
  StockLevelsGateway fakeStockLevelsGateway() {
    return productIds ->
        productIds.stream().anyMatch(silentProducts::contains)
            ? Optional.empty()
            : Optional.of(
                productIds.stream()
                    .filter(stock::containsKey)
                    .collect(Collectors.toMap(Function.identity(), stock::get)));
  }

  @Bean
  @Primary
  OrderNoticesGateway fakeOrderNoticesGateway() {
    return (orderId, customerId) ->
        silentOrders.contains(orderId)
            ? Optional.empty()
            : Optional.of(notices.getOrDefault(orderId, List.of()));
  }
}
