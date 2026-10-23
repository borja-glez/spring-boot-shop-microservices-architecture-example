package com.borjaglez.shop.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/** [DDD · Value Object] Identity of a browsing category, which products can belong to. */
public record CategoryId(UUID value) {

  public CategoryId {
    Objects.requireNonNull(value, "value must not be null");
  }
}
