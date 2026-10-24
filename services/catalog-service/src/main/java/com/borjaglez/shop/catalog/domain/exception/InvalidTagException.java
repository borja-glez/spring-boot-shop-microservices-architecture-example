package com.borjaglez.shop.catalog.domain.exception;

import com.borjaglez.shop.kernel.error.BusinessRuleViolationException;

/** A tag that is empty or too long once slugified. Code {@code invalid-tag}. */
public class InvalidTagException extends BusinessRuleViolationException {

  public InvalidTagException(String message) {
    super("invalid-tag", message);
  }
}
