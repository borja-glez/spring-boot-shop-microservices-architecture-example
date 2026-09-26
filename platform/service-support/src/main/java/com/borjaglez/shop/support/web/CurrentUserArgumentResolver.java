package com.borjaglez.shop.support.web;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@link CurrentUser} parameters from the {@value #HEADER} header. */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

  public static final String HEADER = "X-Shop-User";

  private static final Pattern VALID_USER = Pattern.compile("[A-Za-z0-9._@-]{1,64}");

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentUser.class)
        && String.class.equals(parameter.getParameterType());
  }

  @Override
  public @Nullable Object resolveArgument(
      MethodParameter parameter,
      @Nullable ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      @Nullable WebDataBinderFactory binderFactory) {
    CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
    String user = webRequest.getHeader(HEADER);
    if (user == null || user.isBlank()) {
      if (annotation != null && annotation.required()) {
        throw new MissingUserException();
      }
      return null;
    }
    if (!VALID_USER.matcher(user).matches()) {
      throw new InvalidUserException();
    }
    return user;
  }

  /** The request needs a user and the header is absent. */
  public static class MissingUserException extends RuntimeException {
    MissingUserException() {
      super("This operation needs the " + HEADER + " header.");
    }
  }

  /** The header does not contain a valid user id. */
  public static class InvalidUserException extends RuntimeException {
    InvalidUserException() {
      super(HEADER + " must be 1-64 characters from [A-Za-z0-9._@-].");
    }
  }
}
