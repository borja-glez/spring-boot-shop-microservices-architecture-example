package com.borjaglez.shop.orders.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * What the orders service knows about a catalog product: enough to price an order without asking
 * the catalog. Kept up to date from catalog events.
 *
 * <p>Each attribute group remembers the time of the event that last set it and ignores older
 * events, so the projection converges whatever the delivery order.
 */
@Entity
@Table(name = "catalog_product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogProduct {

  @Id
  @Column(name = "product_id")
  private UUID productId;

  @Column(length = 40)
  private String sku;

  @Column(length = 160)
  private String name;

  @Column(precision = 12, scale = 2)
  private BigDecimal price;

  @Column(length = 3)
  private String currency;

  @Column(nullable = false)
  private boolean available;

  @Column(name = "details_as_of")
  private OffsetDateTime detailsAsOf;

  @Column(name = "price_as_of")
  private OffsetDateTime priceAsOf;

  @Column(name = "availability_as_of")
  private OffsetDateTime availabilityAsOf;

  /** Optimistic lock: concurrent consumers retry instead of overwriting each other. */
  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  /** A product this service has not heard of yet. Not orderable until it is published. */
  public static CatalogProduct unknown(UUID productId) {
    CatalogProduct product = new CatalogProduct();
    product.productId = productId;
    return product;
  }

  public void published(
      String sku, String name, BigDecimal price, String currency, OffsetDateTime at) {
    if (isNewer(at, detailsAsOf)) {
      this.sku = sku;
      this.name = name;
      detailsAsOf = at;
    }
    priceChanged(price, currency, at);
    if (isNewer(at, availabilityAsOf)) {
      available = true;
      availabilityAsOf = at;
    }
  }

  public void priceChanged(BigDecimal price, String currency, OffsetDateTime at) {
    if (isNewer(at, priceAsOf)) {
      this.price = price;
      this.currency = currency;
      priceAsOf = at;
    }
  }

  public void discontinued(OffsetDateTime at) {
    if (isNewer(at, availabilityAsOf)) {
      available = false;
      availabilityAsOf = at;
    }
  }

  /** Orderable: published, not withdrawn, and with a known price and name. */
  public boolean isOrderable() {
    return available && price != null && sku != null;
  }

  private static boolean isNewer(OffsetDateTime candidate, OffsetDateTime current) {
    return current == null || !candidate.isBefore(current);
  }
}
