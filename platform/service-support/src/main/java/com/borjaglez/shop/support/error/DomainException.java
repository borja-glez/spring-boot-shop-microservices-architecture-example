package com.borjaglez.shop.support.error;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Base type for failures that belong to the business domain.
 *
 * <p>It carries a stable, machine-readable {@link #code()} (kebab-case) that clients can rely on.
 * It deliberately has no dependency on Spring or HTTP so that domain models can throw it; the web
 * layer decides which status each subtype maps to.
 */
public abstract class DomainException extends RuntimeException {

  private static final Pattern CODE = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

  private final String code;

  protected DomainException(String code, String message) {
    super(message);
    Objects.requireNonNull(code, "code must not be null");
    if (!CODE.matcher(code).matches()) {
      throw new IllegalArgumentException("code must be kebab-case: " + code);
    }
    this.code = code;
  }

  public String code() {
    return code;
  }
}
