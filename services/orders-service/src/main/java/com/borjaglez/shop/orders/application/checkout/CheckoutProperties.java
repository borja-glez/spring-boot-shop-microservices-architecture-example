package com.borjaglez.shop.orders.application.checkout;

import java.time.Duration;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import com.borjaglez.shop.orders.domain.SagaRetryPolicy;

/**
 * Settings of the checkout saga runner.
 *
 * @param enabled whether the runner advances sagas on its own; tests turn it off to drive the saga
 *     step by step
 * @param pollInterval pause between two looks for due sagas
 * @param batchSize sagas advanced per look
 * @param maxAttempts attempts of a reserve or authorize step before the checkout is rejected; with
 *     the default waits, 8 attempts ride out about two minutes of outage (a rolling restart)
 * @param compensationMaxAttempts attempts of any other step before the saga is flagged as stuck; it
 *     keeps retrying at {@code maxBackoff} anyway
 * @param initialBackoff wait after the first failure of a step; it doubles after each one
 * @param maxBackoff longest wait between two attempts
 * @param lease how long a claimed saga stays with the runner that claimed it (see {@link
 *     DueCheckouts}); longer than any step, which lasts at most the reply timeout
 */
@Validated
@ConfigurationProperties("shop.checkout")
public record CheckoutProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("500ms") Duration pollInterval,
    @DefaultValue("20") @Min(1) int batchSize,
    @DefaultValue("8") @Min(1) int maxAttempts,
    @DefaultValue("20") @Min(1) int compensationMaxAttempts,
    @DefaultValue("1s") Duration initialBackoff,
    @DefaultValue("30s") Duration maxBackoff,
    @DefaultValue("30s") Duration lease) {

  public SagaRetryPolicy retryPolicy() {
    return new SagaRetryPolicy(maxAttempts, compensationMaxAttempts, initialBackoff, maxBackoff);
  }
}
