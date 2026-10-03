package com.borjaglez.shop.support.web;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.borjaglez.shop.support.web.problem.MappedProblem;
import com.borjaglez.shop.support.web.problem.MappedProblem.FieldViolation;
import com.borjaglez.shop.support.web.problem.ProblemMapper;

/**
 * Renders every error as an RFC 9457 {@link ProblemDetail}.
 *
 * <p>Each response carries a stable {@code code}, the request {@code correlationId} and, for
 * validation failures, the list of invalid fields. Framework exceptions keep Spring's status and
 * get a code derived from it. Unknown exceptions become a 500 whose body never includes the
 * exception message.
 */
@RestControllerAdvice
public class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

  public static final String PROBLEM_TYPE_BASE = "https://shop.borjaglez.com/problems/";

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailsExceptionHandler.class);
  private static final int MAX_CAUSE_DEPTH = 10;

  private final List<ProblemMapper> mappers;

  public ProblemDetailsExceptionHandler(List<ProblemMapper> mappers) {
    this.mappers = List.copyOf(mappers);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleAny(Exception exception, NativeWebRequest request) {
    MappedProblem problem =
        resolve(exception)
            .orElseGet(
                () ->
                    MappedProblem.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "internal-error",
                        "An unexpected error occurred. Quote the correlation id when reporting it."));
    log(problem, exception);
    ProblemDetail body = ProblemDetail.forStatusAndDetail(problem.status(), problem.detail());
    decorate(body, problem.code(), problem.errors(), request);
    problem.properties().forEach(body::setProperty);
    return ResponseEntity.status(problem.status()).body(body);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldViolation> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(ProblemDetailsExceptionHandler::toViolation)
            .toList();
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "The request is not valid.");
    decorate(body, "validation-failed", errors, request);
    return handleExceptionInternal(ex, body, headers, status, request);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldViolation> errors =
        ex.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            error ->
                                new FieldViolation(
                                    String.valueOf(result.getMethodParameter().getParameterName()),
                                    error.getDefaultMessage())))
            .toList();
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "The request is not valid.");
    decorate(body, "validation-failed", errors, request);
    return handleExceptionInternal(ex, body, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> createResponseEntity(
      @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    if (body instanceof ProblemDetail problem && !hasCode(problem)) {
      decorate(problem, codeFor(statusCode), List.of(), request);
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }

  private Optional<MappedProblem> resolve(Throwable exception) {
    Set<Throwable> seen = new HashSet<>();
    Throwable current = exception;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (!seen.add(current)) {
        break;
      }
      for (ProblemMapper mapper : mappers) {
        Optional<MappedProblem> mapped = mapper.map(current);
        if (mapped.isPresent()) {
          return mapped;
        }
      }
      current = current.getCause();
    }
    return Optional.empty();
  }

  private static void decorate(
      ProblemDetail body, String code, List<FieldViolation> errors, WebRequest request) {
    body.setType(URI.create(PROBLEM_TYPE_BASE + code));
    body.setProperty("code", code);
    if (body.getInstance() == null
        && request instanceof NativeWebRequest nativeRequest
        && nativeRequest.getNativeRequest() instanceof HttpServletRequest servletRequest) {
      body.setInstance(URI.create(servletRequest.getRequestURI()));
    }
    Object correlationId = request.getAttribute(CorrelationIdFilter.ATTRIBUTE, 0);
    if (correlationId != null) {
      body.setProperty("correlationId", correlationId);
    }
    if (!errors.isEmpty()) {
      body.setProperty("errors", errors);
    }
  }

  private static boolean hasCode(ProblemDetail problem) {
    return problem.getProperties() != null && problem.getProperties().containsKey("code");
  }

  private static FieldViolation toViolation(FieldError error) {
    return new FieldViolation(error.getField(), error.getDefaultMessage());
  }

  private static String codeFor(HttpStatusCode status) {
    HttpStatus resolved = HttpStatus.resolve(status.value());
    if (resolved == null) {
      return "http-" + status.value();
    }
    return resolved.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private static void log(MappedProblem problem, Exception exception) {
    if (problem.status().is5xxServerError()) {
      log.error("Request failed with {}: {}", problem.code(), exception.toString(), exception);
    } else {
      log.debug("Request rejected with {}: {}", problem.code(), exception.toString());
    }
  }
}
