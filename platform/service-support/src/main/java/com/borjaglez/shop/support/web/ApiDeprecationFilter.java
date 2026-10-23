package com.borjaglez.shop.support.web;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.borjaglez.shop.support.web.ApiDeprecationProperties.Deprecation;

/**
 * Announces the deprecated operations of the API in their responses.
 *
 * <p>A request that matches an entry of {@code shop.api.deprecations} (method and Ant path pattern)
 * gets {@code Deprecation: @<epoch seconds>} (RFC 9745), {@code Sunset: <HTTP date>} (RFC 8594)
 * when the entry has a {@code sunset}, and {@code Link: <successor>; rel="successor-version"} when
 * it has a {@code successor}. Without entries the filter does nothing.
 */
public class ApiDeprecationFilter extends OncePerRequestFilter {

  private final List<Deprecation> deprecations;
  private final AntPathMatcher matcher = new AntPathMatcher();

  public ApiDeprecationFilter(List<Deprecation> deprecations) {
    this.deprecations = List.copyOf(deprecations);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return deprecations.isEmpty();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    for (Deprecation deprecation : deprecations) {
      if (deprecation.method().equalsIgnoreCase(request.getMethod())
          && matcher.match(deprecation.path(), path)) {
        announce(deprecation, response);
        break;
      }
    }
    chain.doFilter(request, response);
  }

  private static void announce(Deprecation deprecation, HttpServletResponse response) {
    response.setHeader("Deprecation", "@" + deprecation.deprecatedAt().getEpochSecond());
    if (deprecation.sunset() != null) {
      response.setHeader(
          "Sunset",
          DateTimeFormatter.RFC_1123_DATE_TIME.format(
              deprecation.sunset().atOffset(ZoneOffset.UTC)));
    }
    if (deprecation.successor() != null) {
      response.setHeader("Link", "<" + deprecation.successor() + ">; rel=\"successor-version\"");
    }
  }
}
