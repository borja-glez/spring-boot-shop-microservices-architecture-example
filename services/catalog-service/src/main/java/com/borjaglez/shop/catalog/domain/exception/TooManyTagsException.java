package com.borjaglez.shop.catalog.domain.exception;

import com.borjaglez.shop.kernel.error.BusinessRuleViolationException;

/** A product with more tags than allowed. Code {@code too-many-tags}. */
public class TooManyTagsException extends BusinessRuleViolationException {

  public TooManyTagsException(String message) {
    super("too-many-tags", message);
  }
}
