package com.borjaglez.shop.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.catalog.domain.exception.InvalidSkuException;

class SkuTest {

  @Test
  void trimsAndUppercases() {
    assertThat(new Sku("  caf-001 ").value()).isEqualTo("CAF-001");
  }

  @Test
  void acceptsTheLengthLimits() {
    assertThat(new Sku("a1b").value()).isEqualTo("A1B");
    assertThat(new Sku("A".repeat(40)).value()).hasSize(40);
  }

  @Test
  void rejectsInvalidValues() {
    for (String raw : new String[] {null, "", "  ", "ab", "A".repeat(41), "a b", "caf_1", "café"}) {
      assertThatThrownBy(() -> new Sku(raw))
          .as("sku %s", raw)
          .isInstanceOf(InvalidSkuException.class)
          .hasFieldOrPropertyWithValue("code", "invalid-sku");
    }
  }
}
