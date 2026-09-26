package com.borjaglez.shop.support.web.problem;

import java.util.Optional;

import org.springframework.core.convert.ConversionFailedException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;

import com.borjaglez.specrepository.core.DisallowedFieldException;

/**
 * Maps rejected filters of specification-repository to 400 {@code invalid-filter}: fields outside
 * the whitelist, and values that cannot be converted to the field's type.
 *
 * <p>The library reports a bad value as Spring's generic {@link ConversionFailedException}, without
 * the field name. In a service that uses specification-repository, filter values are the only user
 * input converted this way; request parameters and bodies fail earlier in Spring MVC.
 *
 * <p>A filter the query engine cannot run, such as an operator it does not know ({@code
 * name:like:cafe}), reaches the web layer as a plain {@link IllegalArgumentException} wrapped by
 * the repository proxy in an {@link InvalidDataAccessApiUsageException}. Client plans are the only
 * query input that is not built by the service's own code, so it is a bad request too.
 */
public class SpecificationQueryProblemMapper implements ProblemMapper {

  @Override
  public Optional<MappedProblem> map(Throwable exception) {
    if (exception instanceof DisallowedFieldException disallowed) {
      return Optional.of(
          MappedProblem.of(HttpStatus.BAD_REQUEST, "invalid-filter", disallowed.getMessage()));
    }
    if (exception instanceof ConversionFailedException conversion) {
      return Optional.of(
          MappedProblem.of(
              HttpStatus.BAD_REQUEST,
              "invalid-filter",
              "The value '"
                  + conversion.getValue()
                  + "' is not a valid "
                  + conversion.getTargetType().getType().getSimpleName()
                  + "."));
    }
    if (exception instanceof InvalidDataAccessApiUsageException wrapper
        && wrapper.getCause() != null
        && wrapper.getCause().getClass() == IllegalArgumentException.class) {
      return Optional.of(
          MappedProblem.of(
              HttpStatus.BAD_REQUEST,
              "invalid-filter",
              "The filter is not valid: " + wrapper.getCause().getMessage()));
    }
    return Optional.empty();
  }
}
