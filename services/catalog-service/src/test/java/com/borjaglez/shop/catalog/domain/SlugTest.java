package com.borjaglez.shop.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SlugTest {

  @Test
  void joinsTheSlugifiedNameAndTheLowercaseSku() {
    assertThat(Slug.of("Café de Colombia en grano", new Sku("caf-001")).value())
        .isEqualTo("cafe-de-colombia-en-grano-caf-001");
  }

  @Test
  void dropsPunctuationAndEdgeDashes() {
    assertThat(Slug.of("  ¡Té verde! (50 u.) ", new Sku("TEA-9")).value())
        .isEqualTo("te-verde-50-u-tea-9");
  }

  @Test
  void rejectsBlankValues() {
    assertThatThrownBy(() -> new Slug(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Slug(null)).isInstanceOf(NullPointerException.class);
  }
}
