package com.borjaglez.shop.support.web.problem;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.borjaglez.shop.support.web.CurrentUserArgumentResolver;
import com.borjaglez.shop.support.web.CurrentUserArgumentResolver.InvalidUserException;
import com.borjaglez.shop.support.web.CurrentUserArgumentResolver.MissingUserException;

/**
 * Maps problems with the {@value CurrentUserArgumentResolver#HEADER} header, whether it is read by
 * {@code @CurrentUser} or declared as a constrained {@code @RequestHeader}, as the interfaces
 * generated from the contracts do: an absent header is a 401 {@code missing-user}, an invalid one a
 * 400 {@code invalid-user}.
 *
 * <p>A declared header is checked by {@code UserHeaderInterceptor} before the rest of the request,
 * which raises the exceptions of {@code @CurrentUser}. Where that interceptor is not registered (a
 * standalone MockMvc, for instance), this mapper finds the header among the binding and validation
 * errors.
 */
public class UserHeaderProblemMapper implements ProblemMapper {

  @Override
  public Optional<MappedProblem> map(Throwable exception) {
    return switch (exception) {
      case MissingUserException missing -> Optional.of(missingUser(missing.getMessage()));
      case InvalidUserException invalid -> Optional.of(invalidUser(invalid.getMessage()));
      case MissingRequestHeaderException header
          when CurrentUserArgumentResolver.HEADER.equalsIgnoreCase(header.getHeaderName()) ->
          Optional.of(missingUser(CurrentUserArgumentResolver.MISSING_MESSAGE));
      case HandlerMethodValidationException validation -> invalidHeader(validation);
      default -> Optional.empty();
    };
  }

  /** The violation of the user header, if the method validation found one. */
  private static Optional<MappedProblem> invalidHeader(HandlerMethodValidationException exception) {
    return exception.getParameterValidationResults().stream()
        .filter(
            result ->
                isUserHeader(
                    result.getMethodParameter().getParameterAnnotation(RequestHeader.class)))
        .findFirst()
        .map(
            result ->
                Objects.toString(result.getArgument(), "").isBlank()
                    ? missingUser(CurrentUserArgumentResolver.MISSING_MESSAGE)
                    : invalidUser(CurrentUserArgumentResolver.INVALID_MESSAGE));
  }

  private static boolean isUserHeader(@Nullable RequestHeader header) {
    return header != null && CurrentUserArgumentResolver.HEADER.equalsIgnoreCase(header.name());
  }

  private static MappedProblem missingUser(String detail) {
    return MappedProblem.of(HttpStatus.UNAUTHORIZED, "missing-user", detail);
  }

  private static MappedProblem invalidUser(String detail) {
    return MappedProblem.of(HttpStatus.BAD_REQUEST, "invalid-user", detail);
  }
}
