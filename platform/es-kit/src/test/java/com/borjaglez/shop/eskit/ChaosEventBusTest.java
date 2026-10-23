package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.shop.eskit.fixtures.CounterIncremented;
import com.borjaglez.shop.support.chaos.Chaos;
import com.borjaglez.shop.support.chaos.ChaosException;
import com.borjaglez.shop.support.chaos.ChaosFault;
import com.borjaglez.shop.support.tracing.TraceCarrier;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ChaosEventBusTest {

  private final Chaos chaos = new Chaos();
  private final EventBus kafka = mock(EventBus.class);
  private final ChaosEventBus bus = new ChaosEventBus(kafka, chaos);

  @Test
  void delegatesWhenCalm() {
    CounterIncremented first = new CounterIncremented("c-1", 1);
    CounterIncremented second = new CounterIncremented("c-1", 2);
    CounterIncremented third = new CounterIncremented("c-1", 3);

    bus.publish(first);
    bus.publish(List.of(second, third));

    InOrder order = inOrder(kafka);
    order.verify(kafka).publish(first);
    order.verify(kafka).publish(second);
    order.verify(kafka).publish(third);
    verifyNoMoreInteractions(kafka);
    assertThat(chaos.faults())
        .extracting(ChaosFault::key)
        .contains(RelayFaults.PAUSED, RelayFaults.DUPLICATE);
  }

  @Test
  void registersTheSameFaultsAsTheEventStoreRelay() {
    Chaos eventStoreRelay = new Chaos();
    new OutboxRelay(
        null,
        null,
        null,
        null,
        Clock.systemUTC(),
        100,
        eventStoreRelay,
        TraceCarrier.NONE,
        new SimpleMeterRegistry());

    assertThat(chaos.faults())
        .extracting(ChaosFault::key, ChaosFault::description, ChaosFault::kind)
        .containsExactlyElementsOf(
            eventStoreRelay.faults().stream()
                .map(fault -> tuple(fault.key(), fault.description(), fault.kind()))
                .toList());
  }

  @Test
  void pausedFailsTheBatch() {
    chaos.set(RelayFaults.PAUSED, 1);
    CounterIncremented first = new CounterIncremented("c-1", 1);
    CounterIncremented second = new CounterIncremented("c-1", 2);

    assertThatThrownBy(() -> bus.publish(List.of(first, second)))
        .isInstanceOf(ChaosException.class)
        // The relay logs the failure on every run while paused: one line, no stack trace.
        .satisfies(paused -> assertThat(paused.getStackTrace()).isEmpty());
    verifyNoInteractions(kafka);
  }

  @Test
  void duplicatePublishesTwice() {
    chaos.set(RelayFaults.DUPLICATE, 1);
    CounterIncremented event = new CounterIncremented("c-1", 1);

    bus.publish(event);

    verify(kafka, times(2)).publish(event);
  }
}
