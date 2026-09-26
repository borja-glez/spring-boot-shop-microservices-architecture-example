package com.borjaglez.shop.notifications.api;

import java.net.URI;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.borjaglez.shop.notifications.application.NotificationNotFoundException;

/**
 * RFC 9457 problems with the same {@code code} property as the Boot 4 services, whose shared
 * handler lives in service-support (Boot 4 only).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ProblemHandler {

  private static final String TYPE_BASE = "https://shop.borjaglez.com/problems/";

  @ExceptionHandler(NotificationNotFoundException.class)
  ProblemDetail notFound(NotificationNotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, "notification-not-found", e.getMessage());
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ProblemDetail missingUser(MissingRequestHeaderException e) {
    return problem(
        HttpStatus.UNAUTHORIZED,
        "missing-user",
        "Choose who you are: the " + e.getHeaderName() + " header is missing");
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  ProblemDetail missingParameter(MissingServletRequestParameterException e) {
    return problem(HttpStatus.BAD_REQUEST, "missing-parameter", e.getMessage());
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(TYPE_BASE + code));
    problem.setProperty("code", code);
    return problem;
  }
}
