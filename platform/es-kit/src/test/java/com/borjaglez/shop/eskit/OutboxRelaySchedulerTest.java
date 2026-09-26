package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class OutboxRelaySchedulerTest {

  @Test
  void keepsRelayingAfterFailuresAndErrors() {
    OutboxRelay relay = mock(OutboxRelay.class);
    when(relay.relayPending())
        .thenThrow(new LinkageError("missing reflection hint"))
        .thenThrow(new IllegalStateException("database down"))
        .thenReturn(0);
    OutboxRelayScheduler scheduler = new OutboxRelayScheduler(relay, Duration.ofMillis(10));

    scheduler.start();
    try {
      assertThat(scheduler.isRunning()).isTrue();
      await()
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> verify(relay, atLeast(4)).relayPending());
    } finally {
      scheduler.stop();
    }
    assertThat(scheduler.isRunning()).isFalse();
  }
}
