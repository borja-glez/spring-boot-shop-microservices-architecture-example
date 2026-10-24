package com.borjaglez.shop.catalog.domain.exception;

import com.borjaglez.shop.kernel.error.BusinessRuleViolationException;

/** A price that is not positive or has more than two decimals. Code {@code invalid-price}. */
public class InvalidPriceException extends BusinessRuleViolationException {

  public InvalidPriceException(String message) {
    super("invalid-price", message);
  }
}
