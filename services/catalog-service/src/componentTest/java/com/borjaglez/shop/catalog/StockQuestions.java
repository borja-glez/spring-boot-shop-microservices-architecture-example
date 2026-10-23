package com.borjaglez.shop.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.borjaglez.shop.catalog.application.port.out.StockLevels;
import com.borjaglez.shop.catalog.domain.ProductId;

/**
 * Watches the stock questions the catalog asks the inventory, in front of the adapter that sends
 * them over RabbitMQ: which products each one is about and whether a transaction was open when it
 * was asked. A product page must not hold a database connection while it waits for the inventory.
 */
public class StockQuestions implements StockLevels {

  /** A question on its way to the inventory. */
  public record Question(List<ProductId> products, boolean inTransaction) {}

  private final StockLevels inventory;
  private final List<Question> asked = new CopyOnWriteArrayList<>();

  StockQuestions(StockLevels inventory) {
    this.inventory = inventory;
  }

  @Override
  public Optional<Map<ProductId, Integer>> available(List<ProductId> products) {
    asked.add(
        new Question(
            List.copyOf(products), TransactionSynchronizationManager.isActualTransactionActive()));
    return inventory.available(products);
  }

  /** The questions asked about the product, oldest first. */
  public List<Question> about(ProductId product) {
    return asked.stream().filter(question -> question.products().contains(product)).toList();
  }
}
