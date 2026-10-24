package com.borjaglez.shop.catalog.domain.exception;

import com.borjaglez.shop.kernel.error.BusinessRuleViolationException;

/** A SKU outside 3-40 characters from A-Z, 0-9 and '-'. Code {@code invalid-sku}. */
public class InvalidSkuException extends BusinessRuleViolationException {

  public InvalidSkuException(String message) {
    super("invalid-sku", message);
  }
}
