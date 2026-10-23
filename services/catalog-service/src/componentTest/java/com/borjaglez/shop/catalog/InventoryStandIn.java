package com.borjaglez.shop.catalog;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestComponent;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.contracts.inventory.GetStockLevels;
import com.borjaglez.shop.contracts.inventory.StockLevel;
import com.borjaglez.shop.contracts.inventory.StockLevels;

/**
 * Stand-in for inventory-service: it takes the stock questions of the catalog from RabbitMQ and
 * answers them as the inventory does, with the units each test gives it per product, so tests
 * sharing a context do not see each other's stock. Products it was not told about are unknown to
 * it, and it fails when asked about a product a test said it fails for. It notes every question
 * with the correlation id it arrived with.
 *
 * <p>A {@link TestComponent}, so it is a bean only where {@link CatalogComponentTest} imports it.
 * Its handler method is public, as spring-boot-cqrs calls it.
 */
@TestComponent
@QueryHandler
class InventoryStandIn {

  /** A question as it arrived from RabbitMQ. */
  record Question(List<UUID> productIds, String correlationId) {}

  private final Map<UUID, Integer> units = new ConcurrentHashMap<>();
  private final Set<UUID> failing = ConcurrentHashMap.newKeySet();
  private final List<Question> questions = new CopyOnWriteArrayList<>();

  void stock(UUID productId, int available) {
    units.put(productId, available);
  }

  /** The inventory answers with an error when asked about this product. */
  void failsFor(UUID productId) {
    failing.add(productId);
  }

  /** The questions received about the product, oldest first. */
  List<Question> questionsAbout(UUID productId) {
    return questions.stream()
        .filter(question -> question.productIds().contains(productId))
        .toList();
  }

  @HandleQuery
  public StockLevels levels(GetStockLevels query) {
    questions.add(
        new Question(List.copyOf(query.getProductIds()), MessageContext.current().correlationId()));
    if (query.getProductIds().stream().anyMatch(failing::contains)) {
      throw new IllegalStateException("The warehouse system is down");
    }
    return new StockLevels(
        query.getProductIds().stream()
            .filter(units::containsKey)
            .map(id -> new StockLevel(id, units.get(id)))
            .toList());
  }
}
