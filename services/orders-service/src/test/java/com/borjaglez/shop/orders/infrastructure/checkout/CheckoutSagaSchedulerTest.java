package com.borjaglez.shop.orders.infrastructure.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.orders.application.checkout.CheckoutSagaRunner;

class CheckoutSagaSchedulerTest {

  @Test
  void keepsRunningSagasAfterFailuresAndErrors() {
    CheckoutSagaRunner runner = mock(CheckoutSagaRunner.class);
    when(runner.runDue())
        .thenThrow(new LinkageError("missing reflection hint"))
        .thenThrow(new IllegalStateException("database down"))
        .thenReturn(0);
    CheckoutSagaScheduler scheduler = new CheckoutSagaScheduler(runner, Duration.ofMillis(10));

    scheduler.start();
    try {
      await()
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> verify(runner, atLeast(4)).runDue());
    } finally {
      scheduler.stop();
    }
    assertThat(scheduler.isRunning()).isFalse();
  }
}
