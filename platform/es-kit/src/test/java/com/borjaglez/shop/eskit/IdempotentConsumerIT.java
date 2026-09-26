package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.shop.eskit.fixtures.CounterIncremented;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;

import io.micrometer.core.instrument.MeterRegistry;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class IdempotentConsumerIT {

  @Autowired IdempotentConsumer idempotent;
  @Autowired MeterRegistry meters;

  @Test
  void runsTheEffectOncePerConsumerAndMessage() {
    String messageId = UUID.randomUUID().toString();
    AtomicInteger runs = new AtomicInteger();

    boolean first = idempotent.once("projector-a", messageId, runs::incrementAndGet);
    boolean second = idempotent.once("projector-a", messageId, runs::incrementAndGet);

    assertThat(first).isTrue();
    assertThat(second).isFalse();
    assertThat(runs).hasValue(1);
  }

  @Test
  void eachConsumerSeesTheMessageOnce() {
    String messageId = UUID.randomUUID().toString();
    AtomicInteger runs = new AtomicInteger();

    idempotent.once("projector-a", messageId, runs::incrementAndGet);
    idempotent.once("projector-b", messageId, runs::incrementAndGet);

    assertThat(runs).hasValue(2);
  }

  @Test
  void aFailedEffectCanBeRetried() {
    String messageId = UUID.randomUUID().toString();
    AtomicInteger runs = new AtomicInteger();

    try {
      idempotent.once(
          "projector-a",
          messageId,
          () -> {
            throw new IllegalStateException("boom");
          });
    } catch (IllegalStateException expected) {
      // the transaction rolls back, so the message is not marked as processed
    }
    boolean retried = idempotent.once("projector-a", messageId, runs::incrementAndGet);

    assertThat(retried).isTrue();
    assertThat(runs).hasValue(1);
  }

  @Test
  void countsAppliedAndDuplicateMessagesAndTimesHowLateEventsArrive() {
    CounterIncremented event = new CounterIncremented(UUID.randomUUID().toString(), 1);

    idempotent.once("projector-m", event, () -> {});
    idempotent.once("projector-m", event, () -> {});

    assertThat(count("applied")).isEqualTo(1);
    assertThat(count("duplicate")).isEqualTo(1);
    assertThat(meters.get("shop.consumer.lag").tag("consumer", "projector-m").timer().count())
        .isEqualTo(1);
  }

  private double count(String outcome) {
    return meters
        .get("shop.consumer.events")
        .tags("consumer", "projector-m", "outcome", outcome)
        .counter()
        .count();
  }
}
