package com.borjaglez.shop.support.web.problem;

import java.util.Optional;

import org.springframework.http.HttpStatus;

import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.InvalidFilterException;
import com.borjaglez.specrepository.http.HttpFilterSyntaxException;
import com.borjaglez.specrepository.http.HttpUnknownOperatorException;

/**
 * Maps the client errors of specification-repository to 400 {@code invalid-filter}, with a {@code
 * field} member when the exception names one, as the library's own Problem Details do.
 *
 * <ul>
 *   <li>{@link HttpFilterSyntaxException}: a malformed {@code filter}, {@code orFilter} or {@code
 *       sort}, or one above the {@code specrepository.http.*} limits (too many filters, values or
 *       sort fields, a value too long);
 *   <li>{@link HttpUnknownOperatorException}: an operator outside {@code allowed-operators};
 *   <li>{@link DisallowedFieldException}: a field outside the whitelist, rejected while the
 *       argument is resolved, or a {@code Pageable} sort rejected when the query runs;
 *   <li>{@link InvalidFilterException}: a filter the query engine cannot run, such as a value that
 *       cannot be converted to the field's type.
 * </ul>
 *
 * <p>Since 1.0.0 the library registers an advice that answers these with a plain 400 {@code
 * ProblemDetail}. The shop keeps this mapper so that every error has the same shape ({@code code},
 * {@code type}, {@code correlationId}), and turns the library's advice off ({@code
 * specrepository.http.problem-details.enabled=false} in the shop defaults): the catch-all of {@code
 * ProblemDetailsExceptionHandler} would answer first anyway. The exceptions raised when the query
 * runs arrive wrapped by the repository proxy in an {@code InvalidDataAccessApiUsageException}; the
 * handler walks the causes, so they reach this mapper too.
 */
public class SpecificationProblemMapper implements ProblemMapper {

  @Override
  public Optional<MappedProblem> map(Throwable exception) {
    return switch (exception) {
      case DisallowedFieldException disallowed -> invalidFilter(exception, disallowed.field());
      case InvalidFilterException invalid -> invalidFilter(exception, invalid.field());
      case HttpFilterSyntaxException syntax -> invalidFilter(exception, null);
      case HttpUnknownOperatorException operator -> invalidFilter(exception, null);
      default -> Optional.empty();
    };
  }

  private static Optional<MappedProblem> invalidFilter(Throwable exception, String field) {
    return Optional.of(
        MappedProblem.of(HttpStatus.BAD_REQUEST, "invalid-filter", exception.getMessage())
            .withProperty("field", field));
  }
}
