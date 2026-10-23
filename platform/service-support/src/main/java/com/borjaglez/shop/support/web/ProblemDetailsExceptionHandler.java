package com.borjaglez.shop.support.web;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.borjaglez.shop.support.web.problem.MappedProblem;
import com.borjaglez.shop.support.web.problem.MappedProblem.FieldViolation;
import com.borjaglez.shop.support.web.problem.ProblemMapper;
import com.borjaglez.shop.support.web.problem.UserHeaderProblemMapper;

/**
 * Renders every error as an RFC 9457 {@link ProblemDetail}.
 *
 * <p>Each response carries a stable {@code code}, the request {@code correlationId} and, for
 * validation failures, the list of invalid fields. Framework exceptions keep Spring's status and
 * get a code derived from it. Unknown exceptions become a 500 whose body never includes the
 * exception message. The media type is {@code application/problem+json}, or {@code
 * application/json} for a client that accepts only that.
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
    ResponseEntity.BodyBuilder response = ResponseEntity.status(problem.status());
    if (acceptsJsonButNotProblems(request)) {
      response.contentType(MediaType.APPLICATION_JSON);
    }
    return response.body(body);
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
  protected @Nullable ResponseEntity<Object> handleServletRequestBindingException(
      ServletRequestBindingException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Optional<MappedProblem> userHeader = userHeaderProblem(ex);
    if (userHeader.isPresent()) {
      return render(ex, userHeader.get(), headers, request);
    }
    return super.handleServletRequestBindingException(ex, headers, status, request);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Optional<MappedProblem> userHeader = userHeaderProblem(ex);
    if (userHeader.isPresent()) {
      return render(ex, userHeader.get(), headers, request);
    }
    // A constrained parameter makes Spring MVC validate the whole method, the @Valid body
    // included: the body's errors are field errors and keep their property path, as they do when
    // only the body is validated (MethodArgumentNotValidException).
    List<FieldViolation> errors =
        ex.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            error ->
                                error instanceof FieldError fieldError
                                    ? toViolation(fieldError)
                                    : new FieldViolation(
                                        String.valueOf(
                                            result.getMethodParameter().getParameterName()),
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
    if (acceptsJsonButNotProblems(request)) {
      headers.setContentType(MediaType.APPLICATION_JSON);
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }

  /**
   * Problems are sent as {@code application/problem+json}. A client whose {@code Accept} header
   * admits {@code application/json} but not {@code application/problem+json} gets the same body as
   * {@code application/json}, which the shared error responses of the contracts list too; for any
   * other client Spring negotiates as usual and falls back on {@code application/problem+json}.
   */
  private static boolean acceptsJsonButNotProblems(WebRequest request) {
    String[] accept = request.getHeaderValues(HttpHeaders.ACCEPT);
    if (accept == null) {
      return false;
    }
    List<MediaType> accepted;
    try {
      accepted = MediaType.parseMediaTypes(List.of(accept));
    } catch (InvalidMediaTypeException unreadable) {
      return false;
    }
    return accepted.stream().noneMatch(MediaType.APPLICATION_PROBLEM_JSON::isCompatibleWith)
        && accepted.stream().anyMatch(MediaType.APPLICATION_JSON::isCompatibleWith);
  }

  /** The problems with the user header, which the contracts declare as a header parameter. */
  private Optional<MappedProblem> userHeaderProblem(Exception exception) {
    return mappers.stream()
        .filter(UserHeaderProblemMapper.class::isInstance)
        .findFirst()
        .flatMap(mapper -> mapper.map(exception));
  }

  private ResponseEntity<Object> render(
      Exception exception, MappedProblem problem, HttpHeaders headers, WebRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(problem.status(), problem.detail());
    decorate(body, problem.code(), problem.errors(), request);
    return handleExceptionInternal(exception, body, headers, problem.status(), request);
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
    if (body.getInstance() == null && request instanceof ServletWebRequest servletRequest) {
      body.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
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
