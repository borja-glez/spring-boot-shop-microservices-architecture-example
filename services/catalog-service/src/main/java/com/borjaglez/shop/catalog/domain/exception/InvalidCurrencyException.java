package com.borjaglez.shop.catalog.domain.exception;

import com.borjaglez.shop.kernel.error.BusinessRuleViolationException;

/** A currency code that is not ISO 4217. Code {@code invalid-currency}. */
public class InvalidCurrencyException extends BusinessRuleViolationException {

  public InvalidCurrencyException(String message) {
    super("invalid-currency", message);
  }
}
