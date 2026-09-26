package com.borjaglez.shop.support.web;

/**
 * Makes the correlation id of the current request visible to a subsystem (logging, messaging…) for
 * the duration of the request.
 */
@FunctionalInterface
public interface CorrelationScope {

  /**
   * Binds the id and returns the handle that restores the previous state.
   *
   * @param correlationId id of the current request, never {@code null}
   * @return handle closed when the request completes
   */
  AutoCloseable open(String correlationId);
}
