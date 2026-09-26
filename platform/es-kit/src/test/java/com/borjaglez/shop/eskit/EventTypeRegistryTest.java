package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.shop.eskit.fixtures.CounterIncremented;

class EventTypeRegistryTest {

  private final EventTypeRegistry registry =
      EventTypeRegistry.scanning(
          List.of("com.borjaglez.shop.eskit.fixtures"), new DefaultMessageNamingStrategy("shop"));

  @Test
  void namesEventsWithTheirWireName() {
    assertThat(registry.nameOf(CounterIncremented.class))
        .isEqualTo("shop.eskit-test.1.event.counter.counter-incremented");
  }

  @Test
  void resolvesWireNamesBackToClasses() {
    assertThat(registry.typeOf("shop.eskit-test.1.event.counter.counter-incremented"))
        .isEqualTo(CounterIncremented.class);
  }

  @Test
  void unknownNamesAreRejected() {
    assertThatThrownBy(() -> registry.typeOf("shop.nope.1.event.x.y"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("shop.nope.1.event.x.y");
  }

  @Test
  void eventsWithoutWireNameAreRejected() {
    assertThatThrownBy(() -> registry.nameOf(UnnamedEvent.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("@CqrsMessage");
  }

  static class UnnamedEvent extends com.borjaglez.cqrs.event.Event {}
}
