package com.borjaglez.shop.gateway;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The gateway is where traces start: the trace headers a client sends are dropped before the
 * request is observed. Otherwise a client could join someone else's trace, or switch sampling off
 * for its orders ({@code traceparent} with flags {@code 00}), and that choice would travel with the
 * order through the saga and the events.
 */
class ExternalTraceHeadersFilter extends OncePerRequestFilter {

  static final Set<String> TRACE_HEADERS = Set.of("traceparent", "tracestate", "baggage");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    chain.doFilter(new WithoutTraceHeaders(request), response);
  }

  private static boolean isTraceHeader(String name) {
    return TRACE_HEADERS.contains(name.toLowerCase(Locale.ROOT));
  }

  private static final class WithoutTraceHeaders extends HttpServletRequestWrapper {

    WithoutTraceHeaders(HttpServletRequest request) {
      super(request);
    }

    @Override
    public String getHeader(String name) {
      return isTraceHeader(name) ? null : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      return isTraceHeader(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      return Collections.enumeration(
          Collections.list(super.getHeaderNames()).stream()
              .filter(name -> !isTraceHeader(name))
              .toList());
    }
  }
}
