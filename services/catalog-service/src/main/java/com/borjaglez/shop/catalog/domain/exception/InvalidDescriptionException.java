package com.borjaglez.shop.catalog.domain.exception;

import com.borjaglez.shop.kernel.error.BusinessRuleViolationException;

/** A product description that is too long. Code {@code invalid-description}. */
public class InvalidDescriptionException extends BusinessRuleViolationException {

  public InvalidDescriptionException(String message) {
    super("invalid-description", message);
  }
}
