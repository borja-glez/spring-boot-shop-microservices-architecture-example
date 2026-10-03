package com.borjaglez.shop.support.web.problem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;

/**
 * The HTTP representation chosen for an exception.
 *
 * @param status response status
 * @param code stable kebab-case identifier exposed to clients
 * @param detail human readable explanation, safe to show to clients
 * @param errors per-field problems, empty when not applicable
 * @param properties extra members of the problem, such as the {@code field} of a rejected filter
 */
public record MappedProblem(
    HttpStatus status,
    String code,
    String detail,
    List<FieldViolation> errors,
    Map<String, Object> properties) {

  public MappedProblem {
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(code, "code must not be null");
    errors = errors == null ? List.of() : List.copyOf(errors);
    properties = properties == null ? Map.of() : Map.copyOf(properties);
  }

  public MappedProblem(HttpStatus status, String code, String detail, List<FieldViolation> errors) {
    this(status, code, detail, errors, Map.of());
  }

  public static MappedProblem of(HttpStatus status, String code, String detail) {
    return new MappedProblem(status, code, detail, List.of());
  }

  /** A copy with one more member; a {@code null} value adds nothing. */
  public MappedProblem withProperty(String name, Object value) {
    if (value == null) {
      return this;
    }
    Map<String, Object> more = new LinkedHashMap<>(properties);
    more.put(name, value);
    return new MappedProblem(status, code, detail, errors, more);
  }

  /** A single invalid input. */
  public record FieldViolation(String field, String message) {}
}
