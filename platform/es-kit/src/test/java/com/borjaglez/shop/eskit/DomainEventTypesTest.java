package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.eskit.fixtures.TallyEvent;

class DomainEventTypesTest {

  private static DomainEventTypes tallyTypes() {
    return DomainEventTypes.builder()
        .register("tally-opened", TallyEvent.Opened.class)
        .register("tally-incremented", TallyEvent.Incremented.class)
        .build();
  }

  @Test
  void mapsRegisteredTypesToTheirNamesAndBack() {
    DomainEventTypes types = tallyTypes();

    assertThat(types.nameOf(TallyEvent.Opened.class)).isEqualTo("tally-opened");
    assertThat(types.typeOf("tally-incremented")).isEqualTo(TallyEvent.Incremented.class);
    assertThat(types.types())
        .containsExactly(TallyEvent.Opened.class, TallyEvent.Incremented.class);
  }

  @Test
  void aNameIsRegisteredOnce() {
    DomainEventTypes.Builder builder =
        DomainEventTypes.builder().register("tally-opened", TallyEvent.Opened.class);

    assertThatThrownBy(() -> builder.register("tally-opened", TallyEvent.Incremented.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tally-opened")
        .hasMessageContaining(TallyEvent.Opened.class.getName());
  }

  @Test
  void aTypeIsRegisteredOnce() {
    DomainEventTypes.Builder builder =
        DomainEventTypes.builder().register("tally-opened", TallyEvent.Opened.class);

    assertThatThrownBy(() -> builder.register("tally-started", TallyEvent.Opened.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(TallyEvent.Opened.class.getName())
        .hasMessageContaining("tally-opened");
  }

  @Test
  void unknownNamesAndTypesAreRejected() {
    DomainEventTypes types = tallyTypes();

    assertThatThrownBy(() -> types.typeOf("tally-closed"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tally-closed");
    assertThatThrownBy(() -> types.nameOf(String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(String.class.getName());
  }

  @Test
  void laterRegistrationsDoNotChangeABuiltRegistry() {
    DomainEventTypes.Builder builder =
        DomainEventTypes.builder().register("tally-opened", TallyEvent.Opened.class);
    DomainEventTypes types = builder.build();

    builder.register("tally-incremented", TallyEvent.Incremented.class);

    assertThat(types.types()).containsExactly(TallyEvent.Opened.class);
    assertThatThrownBy(() -> types.types().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
