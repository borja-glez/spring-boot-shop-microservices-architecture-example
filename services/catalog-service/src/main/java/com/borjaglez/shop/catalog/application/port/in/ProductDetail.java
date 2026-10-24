package com.borjaglez.shop.catalog.application.port.in;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.borjaglez.shop.catalog.domain.ProductStatus;

/**
 * A product page.
 *
 * @param categories its categories, ordered by slug
 * @param tags its tags, in alphabetical order
 * @param publishedAt when it went on sale, in UTC
 * @param updatedAt when it last changed, in UTC
 * @param availability the stock of a product on sale; {@code null} for a product that is no longer
 *     on sale
 */
public record ProductDetail(
    UUID id,
    String slug,
    String sku,
    String name,
    String description,
    BigDecimal price,
    String currency,
    ProductStatus status,
    SellerSummary seller,
    List<CategoryRef> categories,
    List<String> tags,
    OffsetDateTime publishedAt,
    OffsetDateTime updatedAt,
    long version,
    Availability availability) {

  /** The same page with the stock the inventory reported. */
  public ProductDetail withAvailability(Availability availability) {
    return new ProductDetail(
        id,
        slug,
        sku,
        name,
        description,
        price,
        currency,
        status,
        seller,
        categories,
        tags,
        publishedAt,
        updatedAt,
        version,
        availability);
  }
}
