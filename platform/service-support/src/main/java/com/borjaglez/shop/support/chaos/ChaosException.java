package com.borjaglez.shop.support.chaos;

/** A technical failure injected on purpose. It is not mapped to a 4xx: it must look real. */
public class ChaosException extends RuntimeException {

  public ChaosException(String message) {
    super(message);
  }

  /**
   * A failure without a stack trace when {@code writableStackTrace} is false, for a fault that
   * fails every run of a scheduled task: the task logs one line per run instead of a full trace.
   */
  public ChaosException(String message, boolean writableStackTrace) {
    super(message, null, false, writableStackTrace);
  }
}
