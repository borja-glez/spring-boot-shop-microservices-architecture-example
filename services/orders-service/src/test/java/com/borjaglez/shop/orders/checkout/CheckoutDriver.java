package com.borjaglez.shop.orders.checkout;

import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;

import com.borjaglez.shop.orders.application.checkout.CheckoutSagaProcessor;
import com.borjaglez.shop.orders.domain.CheckoutSaga;
import com.borjaglez.shop.orders.domain.CheckoutSagaRepository;
import com.borjaglez.specrepository.core.Operators;

/**
 * Advances a checkout saga step by step, as the scheduled runner would, so tests can check the
 * result right away. Import it with the runner off ({@code shop.checkout.enabled=false}) and no
 * backoff.
 */
@TestComponent
public class CheckoutDriver {

  private static final int MAX_STEPS = 50;

  private final CheckoutSagaProcessor processor;
  private final CheckoutSagaRepository sagas;

  public CheckoutDriver(CheckoutSagaProcessor processor, CheckoutSagaRepository sagas) {
    this.processor = processor;
    this.sagas = sagas;
  }

  /** Runs steps until the saga completes or gets stuck, and returns it. */
  public CheckoutSaga finish(UUID orderId) {
    for (int i = 0; i < MAX_STEPS; i++) {
      if (!processor.advance(orderId)) {
        return saga(orderId);
      }
    }
    throw new IllegalStateException("The checkout of " + orderId + " did not finish");
  }

  public CheckoutSaga saga(UUID orderId) {
    return sagas
        .query()
        .where("orderId", Operators.EQUALS, orderId)
        .leftFetch("log")
        .findOne()
        .orElseThrow();
  }
}
