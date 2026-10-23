package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.shop.eskit.fixtures.CounterIncremented;
import com.borjaglez.shop.support.chaos.Chaos;
import com.borjaglez.shop.support.chaos.ChaosException;
import com.borjaglez.shop.support.chaos.ChaosFault;

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
        .contains("relay.paused", "relay.duplicate");
  }

  @Test
  void pausedFailsTheBatch() {
    chaos.set("relay.paused", 1);
    CounterIncremented first = new CounterIncremented("c-1", 1);
    CounterIncremented second = new CounterIncremented("c-1", 2);

    assertThatThrownBy(() -> bus.publish(List.of(first, second)))
        .isInstanceOf(ChaosException.class);
    verifyNoInteractions(kafka);
  }

  @Test
  void duplicatePublishesTwice() {
    chaos.set("relay.duplicate", 1);
    CounterIncremented event = new CounterIncremented("c-1", 1);

    bus.publish(event);

    verify(kafka, times(2)).publish(event);
  }
}
