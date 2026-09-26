package com.borjaglez.shop.inventory.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.borjaglez.shop.support.error.BusinessRuleViolationException;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Units of a catalog product in the warehouse. {@code reserved} units are held for orders whose
 * checkout is running or done; the rest is available.
 */
@Entity
@Table(name = "stock_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockItem {

  @Id
  @Column(name = "product_id")
  private UUID productId;

  @Column(nullable = false, length = 40)
  private String sku;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(name = "on_hand", nullable = false)
  private int onHand;

  @Column(nullable = false)
  private int reserved;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** Two reservations of the same product never overwrite each other: one of them retries. */
  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  public static StockItem stocked(
      UUID productId, String sku, String name, int onHand, OffsetDateTime at) {
    if (onHand < 0) {
      throw new IllegalArgumentException("onHand must not be negative");
    }
    StockItem item = new StockItem();
    item.productId = productId;
    item.sku = sku;
    item.name = name;
    item.onHand = onHand;
    item.updatedAt = at;
    return item;
  }

  public int available() {
    return onHand - reserved;
  }

  /** Holds units for an order. The caller checks availability first. */
  public void reserve(int quantity, OffsetDateTime at) {
    if (quantity < 1 || quantity > available()) {
      throw new IllegalStateException(
          "Cannot reserve " + quantity + " of " + sku + " with " + available() + " available");
    }
    reserved += quantity;
    updatedAt = at;
  }

  /** Gives back units held for an order. */
  public void release(int quantity, OffsetDateTime at) {
    if (quantity < 1 || quantity > reserved) {
      throw new IllegalStateException(
          "Cannot release " + quantity + " of " + sku + " with " + reserved + " reserved");
    }
    reserved -= quantity;
    updatedAt = at;
  }

  /** Sets the units counted in the warehouse. Reserved units cannot disappear. */
  public int adjust(int counted, OffsetDateTime at) {
    if (counted < 0) {
      throw new BusinessRuleViolationException("invalid-stock", "Stock on hand cannot be negative");
    }
    if (counted < reserved) {
      throw new BusinessRuleViolationException(
          "below-reserved",
          "There are "
              + reserved
              + " units of "
              + sku
              + " reserved for orders; stock on hand cannot go below that");
    }
    int previous = onHand;
    onHand = counted;
    updatedAt = at;
    return previous;
  }
}
