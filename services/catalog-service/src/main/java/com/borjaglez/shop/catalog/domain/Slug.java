package com.borjaglez.shop.catalog.domain;

import java.util.Locale;
import java.util.Objects;

/** [DDD · Value Object] URL-safe identifier of a product: its name followed by its SKU. */
public record Slug(String value) {

  public Slug {
    Objects.requireNonNull(value, "value must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }

  public static Slug of(String name, Sku sku) {
    return new Slug(Slugs.slugify(name) + "-" + sku.value().toLowerCase(Locale.ROOT));
  }
}
