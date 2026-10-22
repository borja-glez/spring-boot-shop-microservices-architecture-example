package com.borjaglez.shop.kernel.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SliceTest {

  @Test
  void mapTransformsContentAndKeepsPaging() {
    Slice<String> mapped = new Slice<>(List.of(1, 2), 3, 2, true).map(n -> "n" + n);

    assertThat(mapped.content()).containsExactly("n1", "n2");
    assertThat(mapped.page()).isEqualTo(3);
    assertThat(mapped.size()).isEqualTo(2);
    assertThat(mapped.hasNext()).isTrue();
  }
}
