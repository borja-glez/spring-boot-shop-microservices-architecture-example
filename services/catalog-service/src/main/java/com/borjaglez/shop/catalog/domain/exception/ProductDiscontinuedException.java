package com.borjaglez.shop.catalog.domain.exception;

import com.borjaglez.shop.kernel.error.BusinessRuleViolationException;

/** Changing a product that was withdrawn for good. Code {@code product-discontinued}. */
public class ProductDiscontinuedException extends BusinessRuleViolationException {

  public ProductDiscontinuedException(String message) {
    super("product-discontinued", message);
  }
}
