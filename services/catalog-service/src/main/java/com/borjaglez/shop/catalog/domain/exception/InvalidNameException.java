package com.borjaglez.shop.catalog.domain.exception;

import com.borjaglez.shop.kernel.error.BusinessRuleViolationException;

/** A product name that is blank or too long. Code {@code invalid-name}. */
public class InvalidNameException extends BusinessRuleViolationException {

  public InvalidNameException(String message) {
    super("invalid-name", message);
  }
}
