package com.borjaglez.shop.support.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.borjaglez.shop.support.web.CurrentUserArgumentResolver.InvalidUserException;
import com.borjaglez.shop.support.web.CurrentUserArgumentResolver.MissingUserException;

/**
 * Checks the {@value CurrentUserArgumentResolver#HEADER} header of an operation that declares it as
 * a {@code @RequestHeader} parameter, as the interfaces generated from the contracts do, before
 * Spring MVC reads the rest of the request.
 *
 * <p>Spring MVC resolves the arguments of a handler one after another and validates the constrained
 * ones once all are resolved, so an unreadable body or a malformed path variable would be answered
 * before a missing or invalid user. This check runs before any argument is resolved and answers as
 * {@code @CurrentUser} does: an absent or blank header is a {@link MissingUserException} (401
 * {@code missing-user}) when the parameter is required, a value that is not a user id an {@link
 * InvalidUserException} (400 {@code invalid-user}).
 *
 * <p>It runs once a handler is chosen, so the rejections Spring MVC makes while it maps the request
 * come before it: a method the path does not allow (405), an {@code Accept} header the operation
 * cannot produce (406) and a {@code Content-Type} it does not consume (415).
 */
public class UserHeaderInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (handler instanceof HandlerMethod method) {
      for (MethodParameter parameter : method.getMethodParameters()) {
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && CurrentUserArgumentResolver.HEADER.equalsIgnoreCase(header.name())) {
          CurrentUserArgumentResolver.checked(
              request.getHeader(CurrentUserArgumentResolver.HEADER), header.required());
        }
      }
    }
    return true;
  }
}
