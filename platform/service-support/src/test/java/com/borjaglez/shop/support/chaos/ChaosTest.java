package com.borjaglez.shop.support.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.middleware.MiddlewareChain;
import com.borjaglez.shop.support.error.BusinessRuleViolationException;
import com.borjaglez.shop.support.error.NotFoundException;

class ChaosTest {

  private final Chaos chaos = new Chaos();

  record Ping() {}

  record Other() {}

  private static final MiddlewareChain ECHO = message -> "handled";

  @Test
  void faultsStartOffAndAreListedByKey() {
    chaos.toggle("relay.paused", "Pauses the relay");
    ChaosFault delay = chaos.delay("Ping.delay-ms", "Delays pings");

    assertThat(chaos.faults())
        .extracting(ChaosFault::key)
        .containsExactly("Ping.delay-ms", "relay.paused");
    assertThat(delay.active()).isFalse();
    assertThat(delay.delay()).isZero();
    assertThat(delay.description()).isEqualTo("Delays pings");
  }

  @Test
  void registeringTwiceReturnsTheSameFault() {
    assertThat(chaos.toggle("x", "a")).isSameAs(chaos.toggle("x", "b"));
  }

  @Test
  void aKeyKeepsItsKind() {
    chaos.toggle("x", "a");

    assertThatThrownBy(() -> chaos.delay("x", "a")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void valuesAreChecked() {
    ChaosFault toggle = chaos.toggle("t", "t");
    ChaosFault delay = chaos.delay("d", "d");

    chaos.set("t", 1);
    chaos.set("d", 1500);

    assertThat(toggle.active()).isTrue();
    assertThat(toggle.delay()).isZero();
    assertThat(delay.delay()).isEqualTo(Duration.ofMillis(1500));
    assertThatThrownBy(() -> chaos.set("t", 2)).isInstanceOf(BusinessRuleViolationException.class);
    assertThatThrownBy(() -> chaos.set("d", -1)).isInstanceOf(BusinessRuleViolationException.class);
    assertThatThrownBy(() -> chaos.set("d", ChaosFault.MAX_DELAY_MILLIS + 1))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThatThrownBy(() -> chaos.set("nope", 1)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void resetTurnsEverythingOff() {
    chaos.toggle("t", "t");
    chaos.delay("d", "d");
    chaos.set("t", 1);
    chaos.set("d", 10);

    chaos.reset();

    assertThat(chaos.faults()).allSatisfy(f -> assertThat(f.value()).isZero());
  }

  @Test
  void theMiddlewareLeavesMessagesAloneUntilAFaultIsOn() throws Exception {
    MessageChaosMiddleware middleware = new MessageChaosMiddleware(chaos, List.of("Ping"));

    assertThat(middleware.process(new Ping(), ECHO)).isEqualTo("handled");
    assertThat(chaos.faults())
        .extracting(ChaosFault::key)
        .containsExactly("Ping.delay-ms", "Ping.fail");
  }

  @Test
  void theMiddlewareDelaysAndFailsOnlyItsTypes() throws Exception {
    MessageChaosMiddleware middleware = new MessageChaosMiddleware(chaos, List.of("Ping"));
    chaos.set("Ping.delay-ms", 50);
    chaos.set("Ping.fail", 1);

    long start = System.nanoTime();
    assertThatThrownBy(() -> middleware.process(new Ping(), ECHO))
        .isInstanceOf(ChaosException.class)
        .hasMessageContaining("Ping");
    assertThat(Duration.ofNanos(System.nanoTime() - start))
        .isGreaterThanOrEqualTo(Duration.ofMillis(50));
    assertThat(middleware.process(new Other(), ECHO)).isEqualTo("handled");
  }

  @Test
  void aDelayAloneStillHandlesTheMessage() throws Exception {
    MessageChaosMiddleware middleware = new MessageChaosMiddleware(chaos, List.of("Ping"));
    chaos.set("Ping.delay-ms", 1);

    assertThat(middleware.process(new Ping(), ECHO)).isEqualTo("handled");
  }
}
