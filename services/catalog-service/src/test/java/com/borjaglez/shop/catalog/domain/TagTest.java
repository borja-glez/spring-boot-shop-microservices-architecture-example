package com.borjaglez.shop.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.catalog.domain.exception.InvalidTagException;

class TagTest {

  @Test
  void isSlugified() {
    assertThat(new Tag(" Ecológico ").value()).isEqualTo("ecologico");
    assertThat(new Tag("comercio justo").value()).isEqualTo("comercio-justo");
  }

  @Test
  void acceptsTheLengthLimits() {
    assertThat(new Tag("a").value()).isEqualTo("a");
    assertThat(new Tag("a".repeat(30)).value()).hasSize(30);
  }

  @Test
  void rejectsInvalidValues() {
    for (String raw : new String[] {null, "", "  ", "!!!", "a".repeat(31)}) {
      assertThatThrownBy(() -> new Tag(raw))
          .as("tag %s", raw)
          .isInstanceOf(InvalidTagException.class)
          .hasFieldOrPropertyWithValue("code", "invalid-tag");
    }
  }
}
