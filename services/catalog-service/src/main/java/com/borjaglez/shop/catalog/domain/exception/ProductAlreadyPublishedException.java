package com.borjaglez.shop.catalog.domain.exception;

import com.borjaglez.shop.kernel.error.BusinessRuleViolationException;

/** Publishing a product that is already on sale. Code {@code product-already-published}. */
public class ProductAlreadyPublishedException extends BusinessRuleViolationException {

  public ProductAlreadyPublishedException(String message) {
    super("product-already-published", message);
  }
}
