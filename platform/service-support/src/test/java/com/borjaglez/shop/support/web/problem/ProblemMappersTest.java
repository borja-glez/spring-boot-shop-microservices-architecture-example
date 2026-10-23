package com.borjaglez.shop.support.web.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.RequestHeader;

import com.borjaglez.shop.kernel.error.DomainException;
import com.borjaglez.shop.support.web.problem.MappedProblem.FieldViolation;

class ProblemMappersTest {

  /** A domain failure of a kind the mapper does not know. */
  static class OtherDomainException extends DomainException {
    OtherDomainException() {
      super("other-failure", "Something else");
    }
  }

  @SuppressWarnings("unused")
  void handler(@RequestHeader("X-Other") String other) {}

  @Test
  void anUnknownKindOfDomainFailureIsUnprocessable() {
    assertThat(new DomainProblemMapper().map(new OtherDomainException()))
        .hasValueSatisfying(
            problem -> {
              assertThat(problem.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(problem.code()).isEqualTo("other-failure");
            });
  }

  @Test
  void domainMapperIgnoresOtherExceptions() {
    assertThat(new DomainProblemMapper().map(new IllegalStateException("x"))).isEmpty();
  }

  @Test
  void dataIntegrityViolationIsAConflict() {
    assertThat(new DataAccessProblemMapper().map(new DataIntegrityViolationException("dup")))
        .hasValueSatisfying(
            problem -> {
              assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.code()).isEqualTo("data-integrity-violation");
            });
    assertThat(new DataAccessProblemMapper().map(new IllegalStateException("x"))).isEmpty();
  }

  @Test
  void aMissingHeaderOtherThanTheUserIsNotMapped() throws Exception {
    Method method = ProblemMappersTest.class.getDeclaredMethod("handler", String.class);
    var exception = new MissingRequestHeaderException("X-Other", new MethodParameter(method, 0));

    assertThat(new UserHeaderProblemMapper().map(exception)).isEmpty();
  }

  @Test
  void aProblemWithoutErrorsOrPropertiesHasNone() {
    var problem = new MappedProblem(HttpStatus.BAD_REQUEST, "bad", "Bad", null, null);

    assertThat(problem.errors()).isEmpty();
    assertThat(problem.properties()).isEmpty();
    assertThat(new MappedProblem(HttpStatus.BAD_REQUEST, "bad", "Bad", null).errors()).isEmpty();
    assertThat(
            new MappedProblem(
                    HttpStatus.BAD_REQUEST, "bad", "Bad", List.of(new FieldViolation("a", "b")))
                .errors())
        .hasSize(1);
  }

  @Test
  void constraintViolationsListTheirPropertyPaths() {
    record Thing(@NotBlank String name, @Positive int size) {}
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var violations = factory.getValidator().validate(new Thing("", 0));

      assertThat(new ValidationProblemMapper().map(new ConstraintViolationException(violations)))
          .hasValueSatisfying(
              problem ->
                  assertThat(problem.errors())
                      .extracting(FieldViolation::field)
                      .containsExactly("name", "size"));
    }
  }
}
