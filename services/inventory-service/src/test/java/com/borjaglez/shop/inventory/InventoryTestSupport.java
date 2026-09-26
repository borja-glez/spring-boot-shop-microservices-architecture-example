package com.borjaglez.shop.inventory;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;

import com.borjaglez.shop.contracts.inventory.ReservationLine;
import com.borjaglez.shop.inventory.domain.StockItem;
import com.borjaglez.shop.inventory.domain.StockItemRepository;
import com.borjaglez.specrepository.core.Operators;

/** Creates stock and reads it back for the integration tests. */
@TestComponent
public class InventoryTestSupport {

  private final StockItemRepository stock;

  public InventoryTestSupport(StockItemRepository stock) {
    this.stock = stock;
  }

  /** A new product with the given units on hand. */
  public ReservationLine product(int onHand, int wanted) {
    UUID id = UUID.randomUUID();
    String sku = "INV-" + id.toString().substring(0, 6);
    stock.save(StockItem.stocked(id, sku, "Producto " + sku, onHand, OffsetDateTime.now()));
    return new ReservationLine(id, sku, wanted);
  }

  public StockItem item(UUID productId) {
    return stock.query().where("productId", Operators.EQUALS, productId).findOne().orElseThrow();
  }
}
