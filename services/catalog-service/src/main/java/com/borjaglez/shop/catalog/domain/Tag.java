package com.borjaglez.shop.catalog.domain;

import com.borjaglez.shop.catalog.domain.exception.InvalidTagException;

/** [DDD · Value Object] A free-form label of a product, slugified, 1-30 characters. */
public record Tag(String value) {

  static final int MAX_LENGTH = 30;

  public Tag {
    value = value == null ? "" : Slug.slugify(value);
    if (value.isEmpty() || value.length() > MAX_LENGTH) {
      throw new InvalidTagException("Tags must have between 1 and " + MAX_LENGTH + " characters");
    }
  }
}
