package com.borjaglez.shop.support.web.problem;

import java.util.Optional;

import org.springframework.http.HttpStatus;

import com.borjaglez.specrepository.http.HttpFilterSyntaxException;
import com.borjaglez.specrepository.http.HttpUnknownOperatorException;

/**
 * Maps malformed {@code filter}, {@code orFilter} and {@code sort} parameters parsed by the
 * specification-repository HTTP module to 400 {@code invalid-filter}.
 */
public class SpecificationHttpProblemMapper implements ProblemMapper {

  @Override
  public Optional<MappedProblem> map(Throwable exception) {
    if (exception instanceof HttpFilterSyntaxException
        || exception instanceof HttpUnknownOperatorException) {
      return Optional.of(
          MappedProblem.of(HttpStatus.BAD_REQUEST, "invalid-filter", exception.getMessage()));
    }
    return Optional.empty();
  }
}
