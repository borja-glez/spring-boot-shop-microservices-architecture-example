package com.borjaglez.shop.support.web.problem;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;

/** Maps concurrency and constraint failures raised by the persistence layer to 409. */
public class DataAccessProblemMapper implements ProblemMapper {

  @Override
  public Optional<MappedProblem> map(Throwable exception) {
    if (exception instanceof OptimisticLockingFailureException) {
      return Optional.of(
          MappedProblem.of(
              HttpStatus.CONFLICT,
              "concurrent-modification",
              "The resource was modified by another request. Reload it and try again."));
    }
    if (exception instanceof DataIntegrityViolationException) {
      return Optional.of(
          MappedProblem.of(
              HttpStatus.CONFLICT,
              "data-integrity-violation",
              "The request conflicts with data that already exists."));
    }
    return Optional.empty();
  }
}
