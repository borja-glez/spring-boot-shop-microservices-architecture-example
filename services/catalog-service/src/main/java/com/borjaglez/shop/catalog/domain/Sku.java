package com.borjaglez.shop.catalog.domain;

import java.util.Locale;
import java.util.regex.Pattern;

import com.borjaglez.shop.catalog.domain.exception.InvalidSkuException;

/**
 * [DDD · Value Object] Stock keeping unit: 3-40 characters from A-Z, 0-9 and a dash, trimmed and
 * upper-cased.
 */
public record Sku(String value) {

  private static final Pattern FORMAT = Pattern.compile("[A-Z0-9-]{3,40}");

  public Sku {
    value = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    if (!FORMAT.matcher(value).matches()) {
      throw new InvalidSkuException("SKU must be 3-40 characters from A-Z, 0-9 and '-'");
    }
  }
}
