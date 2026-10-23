package com.borjaglez.shop.catalog.application.exception;

import com.borjaglez.shop.catalog.domain.Sku;
import com.borjaglez.shop.kernel.error.ConflictException;

/** Another product already uses the SKU. Code {@code duplicate-sku}. */
public final class DuplicateSku extends ConflictException {

  public DuplicateSku(Sku sku) {
    super("duplicate-sku", "Another product already uses SKU " + sku.value());
  }
}
