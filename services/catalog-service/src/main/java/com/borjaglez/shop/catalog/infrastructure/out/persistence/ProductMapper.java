package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.borjaglez.shop.catalog.domain.CategoryEntity;
import com.borjaglez.shop.catalog.domain.CategoryId;
import com.borjaglez.shop.catalog.domain.Money;
import com.borjaglez.shop.catalog.domain.MoneyEmbeddable;
import com.borjaglez.shop.catalog.domain.Product.ProductState;
import com.borjaglez.shop.catalog.domain.ProductEntity;
import com.borjaglez.shop.catalog.domain.ProductId;
import com.borjaglez.shop.catalog.domain.SellerEntity;
import com.borjaglez.shop.catalog.domain.SellerId;
import com.borjaglez.shop.catalog.domain.Sku;
import com.borjaglez.shop.catalog.domain.Slug;
import com.borjaglez.shop.catalog.domain.Tag;

/**
 * Translates between the state of a {@code Product} and its stored form. Instants are stored in
 * UTC.
 */
final class ProductMapper {

  private ProductMapper() {}

  static ProductState toState(ProductEntity entity) {
    return new ProductState(
        new ProductId(entity.getId()),
        new Sku(entity.getSku()),
        new Slug(entity.getSlug()),
        entity.getName(),
        entity.getDescription(),
        new Money(entity.getPrice().amount(), entity.getPrice().currency()),
        entity.getStatus(),
        new SellerId(entity.getSeller().getId()),
        entity.getCategories().stream()
            .map(category -> new CategoryId(category.getId()))
            .collect(Collectors.toCollection(LinkedHashSet::new)),
        entity.getTags().stream()
            .map(Tag::new)
            .collect(Collectors.toCollection(LinkedHashSet::new)),
        instant(entity.getCreatedAt()),
        instant(entity.getPublishedAt()),
        instant(entity.getUpdatedAt()),
        entity.getVersion());
  }

  /**
   * The entity of a product state, with its seller and categories given as entities (references are
   * enough: only their ids are stored).
   */
  static ProductEntity toEntity(
      ProductState state, SellerEntity seller, Set<CategoryEntity> categories) {
    return new ProductEntity(
        state.id().value(),
        state.sku().value(),
        state.slug().value(),
        state.name(),
        state.description(),
        new MoneyEmbeddable(state.price().amount(), state.price().currency()),
        state.status(),
        seller,
        categories,
        state.tags().stream().map(Tag::value).collect(Collectors.toCollection(LinkedHashSet::new)),
        utc(state.createdAt()),
        utc(state.publishedAt()),
        utc(state.updatedAt()),
        state.version());
  }

  private static Instant instant(OffsetDateTime dateTime) {
    return dateTime == null ? null : dateTime.toInstant();
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
