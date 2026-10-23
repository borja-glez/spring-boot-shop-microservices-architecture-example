package com.borjaglez.shop.catalog.application.exception;

import com.borjaglez.shop.catalog.domain.Sku;
import com.borjaglez.shop.kernel.error.ConflictException;

/** Another product already uses the SKU. Code {@code duplicate-sku}. */
public final class DuplicateSkuException extends ConflictException {

  public DuplicateSkuException(Sku sku) {
    super("duplicate-sku", "Another product already uses SKU " + sku.value());
  }
}
