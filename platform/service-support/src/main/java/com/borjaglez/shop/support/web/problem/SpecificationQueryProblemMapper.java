package com.borjaglez.shop.support.web.problem;

import java.util.Optional;

import org.springframework.http.HttpStatus;

import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.InvalidFilterException;

/**
 * Maps rejected filters of specification-repository to 400 {@code invalid-filter}: fields outside
 * the whitelist ({@link DisallowedFieldException}), and filters the query engine cannot run ({@link
 * InvalidFilterException}), such as a value that cannot be converted to the field's type or an
 * unknown field or operator.
 *
 * <p>The argument resolver of {@code @FilterableQuery} rejects disallowed fields before the handler
 * runs; the other errors surface when the query runs, wrapped by the repository proxy in an {@code
 * InvalidDataAccessApiUsageException}. {@code ProblemDetailsExceptionHandler} walks the causes, so
 * both reach this mapper.
 */
public class SpecificationQueryProblemMapper implements ProblemMapper {

  @Override
  public Optional<MappedProblem> map(Throwable exception) {
    if (exception instanceof DisallowedFieldException
        || exception instanceof InvalidFilterException) {
      return Optional.of(
          MappedProblem.of(HttpStatus.BAD_REQUEST, "invalid-filter", exception.getMessage()));
    }
    return Optional.empty();
  }
}
