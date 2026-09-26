package com.borjaglez.shop.orders.domain;

import java.time.Duration;

/**
 * How the checkout saga retries a step after a technical failure.
 *
 * @param maxAttempts attempts of a forward step (reserve, authorize) before the checkout is
 *     rejected
 * @param compensationMaxAttempts attempts of any other step before the saga is marked stuck
 * @param initialBackoff wait after the first failure; it doubles after each one
 * @param maxBackoff longest wait between attempts
 */
public record SagaRetryPolicy(
    int maxAttempts, int compensationMaxAttempts, Duration initialBackoff, Duration maxBackoff) {

  public SagaRetryPolicy {
    if (maxAttempts < 1 || compensationMaxAttempts < 1) {
      throw new IllegalArgumentException("Attempts must be at least 1");
    }
  }

  /** Wait after the given number of failed attempts, from 1. */
  public Duration backoff(int failedAttempts) {
    int doublings = Math.min(Math.max(failedAttempts - 1, 0), 20);
    Duration wait = initialBackoff.multipliedBy(1L << doublings);
    return wait.compareTo(maxBackoff) > 0 ? maxBackoff : wait;
  }

  int limitFor(CheckoutStep step) {
    return step == CheckoutStep.RESERVE_STOCK || step == CheckoutStep.AUTHORIZE_PAYMENT
        ? maxAttempts
        : compensationMaxAttempts;
  }
}
