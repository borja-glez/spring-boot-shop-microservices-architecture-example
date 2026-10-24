package com.borjaglez.shop.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/** [DDD · Value Object] Identity of a {@link Product}. */
public record ProductId(UUID value) {

  public ProductId {
    Objects.requireNonNull(value, "value must not be null");
  }

  public static ProductId newId() {
    return new ProductId(UUID.randomUUID());
  }
}
