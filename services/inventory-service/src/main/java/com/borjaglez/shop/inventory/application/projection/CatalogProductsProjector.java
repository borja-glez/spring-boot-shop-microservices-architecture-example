package com.borjaglez.shop.inventory.application.projection;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.idempotency.Idempotent;
import com.borjaglez.shop.contracts.catalog.ProductPublished;
import com.borjaglez.shop.inventory.domain.StockItem;
import com.borjaglez.shop.inventory.domain.StockItemRepository;
import com.borjaglez.specrepository.core.Operators;

/**
 * Starts keeping stock for every product the catalog publishes. Republishing a product (after a
 * relay retry, for example) leaves its stock untouched.
 */
@EventHandler
public class CatalogProductsProjector {

  static final String CONSUMER = "inventory.catalog-products";

  private final StockItemRepository stock;
  private final InventoryProperties properties;
  private final Clock clock;

  public CatalogProductsProjector(
      StockItemRepository stock, InventoryProperties properties, Clock clock) {
    this.stock = stock;
    this.properties = properties;
    this.clock = clock;
  }

  @HandleEvent
  @Idempotent(name = CONSUMER)
  @Transactional
  public void on(ProductPublished event) {
    boolean known =
        stock.query().where("productId", Operators.EQUALS, event.getProductId()).count() > 0;
    if (!known) {
      stock.save(
          StockItem.stocked(
              event.getProductId(),
              event.getSku(),
              event.getName(),
              properties.initialStock(),
              OffsetDateTime.now(clock)));
    }
  }
}
