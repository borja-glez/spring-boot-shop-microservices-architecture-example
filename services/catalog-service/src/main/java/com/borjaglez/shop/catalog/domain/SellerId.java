package com.borjaglez.shop.catalog.domain;

import java.util.Objects;

/** [DDD · Value Object] Identity of a seller: the user id sent by the client. */
public record SellerId(String value) {

  public SellerId {
    Objects.requireNonNull(value, "value must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }
}
