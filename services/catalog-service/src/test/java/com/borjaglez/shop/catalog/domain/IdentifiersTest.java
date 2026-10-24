package com.borjaglez.shop.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class IdentifiersTest {

  @Test
  void productIdsAreGeneratedUnique() {
    assertThat(ProductId.newId()).isNotEqualTo(ProductId.newId());
  }

  @Test
  void identifiersRejectNull() {
    assertThatThrownBy(() -> new ProductId(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new CategoryId(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new SellerId(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void sellerIdRejectsBlank() {
    assertThatThrownBy(() -> new SellerId("  ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void identifiersCompareByValue() {
    UUID uuid = UUID.randomUUID();
    assertThat(new ProductId(uuid)).isEqualTo(new ProductId(uuid));
    assertThat(new CategoryId(uuid)).isEqualTo(new CategoryId(uuid));
    assertThat(new SellerId("seller-ana")).isEqualTo(new SellerId("seller-ana"));
  }
}
