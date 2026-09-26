package com.borjaglez.shop.support.web;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id.
 *
 * <p>The id is taken from the {@value #HEADER} header when it is safe (1–64 characters from {@code
 * [A-Za-z0-9._-]}); otherwise a random UUID is generated. It is echoed in the response, stored as
 * the request attribute {@value #ATTRIBUTE}, put in the logging MDC under {@value #MDC_KEY} and
 * handed to every registered {@link CorrelationScope} (for example the CQRS message context), so
 * commands and events dispatched while serving the request carry it too.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Correlation-Id";
  public static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";
  public static final String MDC_KEY = "correlationId";

  private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
  private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  private final List<CorrelationScope> scopes;

  public CorrelationIdFilter(List<CorrelationScope> scopes) {
    this.scopes = List.copyOf(scopes);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String correlationId = resolve(request.getHeader(HEADER));
    request.setAttribute(ATTRIBUTE, correlationId);
    response.setHeader(HEADER, correlationId);
    MDC.put(MDC_KEY, correlationId);
    Deque<AutoCloseable> opened = new ArrayDeque<>();
    try {
      for (CorrelationScope scope : scopes) {
        opened.push(scope.open(correlationId));
      }
      chain.doFilter(request, response);
    } finally {
      while (!opened.isEmpty()) {
        close(opened.pop());
      }
      MDC.remove(MDC_KEY);
    }
  }

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  static String resolve(String candidate) {
    if (candidate != null && SAFE_ID.matcher(candidate).matches()) {
      return candidate;
    }
    return UUID.randomUUID().toString();
  }

  private static void close(AutoCloseable handle) {
    try {
      handle.close();
    } catch (Exception e) {
      log.warn("Could not release correlation scope", e);
    }
  }
}
