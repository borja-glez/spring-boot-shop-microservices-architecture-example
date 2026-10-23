package com.borjaglez.shop.catalog;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.borjaglez.shop.catalog.application.port.out.StockLevels;
import com.borjaglez.shop.catalog.domain.ProductId;

/**
 * An inventory that answers as each test tells it, per product, so tests sharing a context do not
 * see each other's stock. Products it was not told about are unknown to it. It notes whether it was
 * ever asked inside a transaction: a product page must not hold a database connection while it
 * waits for the inventory.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeStockLevels {

  private final Map<ProductId, Integer> units = new ConcurrentHashMap<>();
  private final Set<ProductId> silent = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean askedInATransaction = new AtomicBoolean();

  public void stock(UUID productId, int available) {
    units.put(new ProductId(productId), available);
  }

  /** The inventory does not answer when asked about this product. */
  public void silentAbout(UUID productId) {
    silent.add(new ProductId(productId));
  }

  /** Whether any question reached the inventory while a transaction was open. */
  public boolean wasAskedInATransaction() {
    return askedInATransaction.get();
  }

  @Bean
  @Primary
  StockLevels fakeStockLevels() {
    return products -> {
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        askedInATransaction.set(true);
      }
      return products.stream().anyMatch(silent::contains)
          ? Optional.empty()
          : Optional.of(
              products.stream()
                  .filter(units::containsKey)
                  .collect(Collectors.toMap(Function.identity(), units::get)));
    };
  }
}
