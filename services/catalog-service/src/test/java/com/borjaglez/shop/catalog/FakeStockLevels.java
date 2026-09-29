package com.borjaglez.shop.catalog;

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

import com.borjaglez.shop.catalog.application.query.StockLevelsGateway;

/**
 * An inventory that answers as each test tells it, per product, so tests sharing a context do not
 * see each other's stock. Products it was not told about are unknown to it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeStockLevels {

  private final Map<UUID, Integer> units = new ConcurrentHashMap<>();
  private final Set<UUID> silent = ConcurrentHashMap.newKeySet();

  public void stock(UUID productId, int available) {
    units.put(productId, available);
  }

  /** The inventory does not answer when asked about this product. */
  public void silentAbout(UUID productId) {
    silent.add(productId);
  }

  @Bean
  @Primary
  StockLevelsGateway fakeStockLevelsGateway() {
    return productIds ->
        productIds.stream().anyMatch(silent::contains)
            ? Optional.empty()
            : Optional.of(
                productIds.stream()
                    .filter(units::containsKey)
                    .collect(Collectors.toMap(Function.identity(), units::get)));
  }
}
