package com.borjaglez.shop.catalog.application.port.in;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.borjaglez.shop.catalog.domain.ProductStatus;

/**
 * A product in a search result.
 *
 * @param categories the slugs of its categories, in alphabetical order
 * @param tags its tags, in alphabetical order
 * @param publishedAt when it went on sale, in UTC
 */
public record ProductCard(
    UUID id,
    String slug,
    String sku,
    String name,
    BigDecimal price,
    String currency,
    ProductStatus status,
    SellerSummary seller,
    List<String> categories,
    List<String> tags,
    OffsetDateTime publishedAt) {}
