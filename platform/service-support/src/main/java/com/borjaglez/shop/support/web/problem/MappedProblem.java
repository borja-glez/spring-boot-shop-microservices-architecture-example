package com.borjaglez.shop.support.web.problem;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;

/**
 * The HTTP representation chosen for an exception.
 *
 * @param status response status
 * @param code stable kebab-case identifier exposed to clients
 * @param detail human readable explanation, safe to show to clients
 * @param errors per-field problems, empty when not applicable
 */
public record MappedProblem(
    HttpStatus status, String code, String detail, List<FieldViolation> errors) {

  public MappedProblem {
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(code, "code must not be null");
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public static MappedProblem of(HttpStatus status, String code, String detail) {
    return new MappedProblem(status, code, detail, List.of());
  }

  /** A single invalid input. */
  public record FieldViolation(String field, String message) {}
}
