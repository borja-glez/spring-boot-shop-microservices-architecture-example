package com.borjaglez.shop.catalog.application.exception;

import java.util.Collection;
import java.util.TreeSet;

import com.borjaglez.shop.kernel.error.BusinessRuleViolationException;

/** A product placed in categories the catalog does not have. Code {@code unknown-category}. */
public final class UnknownCategory extends BusinessRuleViolationException {

  public UnknownCategory(Collection<String> slugs) {
    super("unknown-category", "Unknown categories: " + String.join(", ", new TreeSet<>(slugs)));
  }
}
