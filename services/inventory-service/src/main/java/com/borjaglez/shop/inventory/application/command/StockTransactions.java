package com.borjaglez.shop.inventory.application.command;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs the transactions that change stock, again when another one changed the same rows first.
 *
 * <p>Stock rows are versioned: when many checkouts want the same product at once, most of their
 * transactions lose the race and fail on commit. That is contention inside the inventory, not an
 * outage, so it is retried here with a short random pause instead of going back to the saga as a
 * technical failure. Otherwise every saga retries at the same moment, collides again, and some give
 * up and reject orders the warehouse could serve (found by the system tests under a burst).
 *
 * <p>Deadlocks and lock timeouts ({@code CannotAcquireLockException}) are the same contention seen
 * by the database, so every {@link ConcurrencyFailureException} is retried. {@code
 * hibernate.order_updates} makes them rare: all transactions update stock rows in key order.
 */
@Component
public class StockTransactions {

  static final int ATTEMPTS = 15;

  private final TransactionTemplate transactions;

  public StockTransactions(TransactionTemplate transactions) {
    this.transactions = transactions;
  }

  public <T> T run(Supplier<T> work) {
    for (int attempt = 1; ; attempt++) {
      try {
        return transactions.execute(status -> work.get());
      } catch (ConcurrencyFailureException e) {
        if (attempt == ATTEMPTS) {
          throw e;
        }
        pause(attempt);
      }
    }
  }

  private static void pause(int attempt) {
    try {
      Thread.sleep(ThreadLocalRandom.current().nextLong(1, 10L * attempt + 1));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while retrying a stock change", e);
    }
  }
}
