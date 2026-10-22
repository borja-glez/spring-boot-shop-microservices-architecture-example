package com.borjaglez.shop.support.web.problem;

import java.util.Optional;

import org.springframework.http.HttpStatus;

import com.borjaglez.shop.kernel.error.BusinessRuleViolationException;
import com.borjaglez.shop.kernel.error.ConflictException;
import com.borjaglez.shop.kernel.error.DomainException;
import com.borjaglez.shop.kernel.error.NotFoundException;

/** Maps {@link DomainException} subtypes to 404, 409 and 422. */
public class DomainProblemMapper implements ProblemMapper {

  @Override
  public Optional<MappedProblem> map(Throwable exception) {
    if (!(exception instanceof DomainException domain)) {
      return Optional.empty();
    }
    return Optional.of(MappedProblem.of(statusOf(domain), domain.code(), domain.getMessage()));
  }

  private static HttpStatus statusOf(DomainException exception) {
    return switch (exception) {
      case NotFoundException ignored -> HttpStatus.NOT_FOUND;
      case ConflictException ignored -> HttpStatus.CONFLICT;
      case BusinessRuleViolationException ignored -> HttpStatus.UNPROCESSABLE_CONTENT;
      default -> HttpStatus.UNPROCESSABLE_CONTENT;
    };
  }
}
