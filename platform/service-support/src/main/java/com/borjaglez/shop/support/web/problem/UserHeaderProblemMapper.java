package com.borjaglez.shop.support.web.problem;

import java.util.Optional;

import org.springframework.http.HttpStatus;

import com.borjaglez.shop.support.web.CurrentUserArgumentResolver;
import com.borjaglez.shop.support.web.CurrentUserArgumentResolver.InvalidUserException;
import com.borjaglez.shop.support.web.CurrentUserArgumentResolver.MissingUserException;

/** Maps problems with the {@value CurrentUserArgumentResolver#HEADER} header. */
public class UserHeaderProblemMapper implements ProblemMapper {

  @Override
  public Optional<MappedProblem> map(Throwable exception) {
    return switch (exception) {
      case MissingUserException missing ->
          Optional.of(
              MappedProblem.of(HttpStatus.UNAUTHORIZED, "missing-user", missing.getMessage()));
      case InvalidUserException invalid ->
          Optional.of(
              MappedProblem.of(HttpStatus.BAD_REQUEST, "invalid-user", invalid.getMessage()));
      default -> Optional.empty();
    };
  }
}
