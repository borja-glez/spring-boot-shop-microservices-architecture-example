package com.borjaglez.shop.catalog.domain.exception;

import com.borjaglez.shop.kernel.error.BusinessRuleViolationException;

/** A price change in a currency other than the product's. Code {@code currency-mismatch}. */
public class CurrencyMismatchException extends BusinessRuleViolationException {

  public CurrencyMismatchException(String message) {
    super("currency-mismatch", message);
  }
}
