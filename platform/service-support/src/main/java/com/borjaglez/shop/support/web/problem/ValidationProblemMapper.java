package com.borjaglez.shop.support.web.problem;

import java.util.Comparator;
import java.util.Optional;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;

import com.borjaglez.shop.support.web.problem.MappedProblem.FieldViolation;

/**
 * Maps Bean Validation failures raised outside Spring MVC binding (for example by the command bus
 * validation middleware) to 400 {@code validation-failed}.
 */
public class ValidationProblemMapper implements ProblemMapper {

  @Override
  public Optional<MappedProblem> map(Throwable exception) {
    if (!(exception instanceof ConstraintViolationException violation)) {
      return Optional.empty();
    }
    var errors =
        violation.getConstraintViolations().stream()
            .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
            .sorted(Comparator.comparing(FieldViolation::field))
            .toList();
    return Optional.of(
        new MappedProblem(
            HttpStatus.BAD_REQUEST, "validation-failed", "The request is not valid.", errors));
  }
}
