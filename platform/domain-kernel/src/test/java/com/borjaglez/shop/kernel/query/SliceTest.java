package com.borjaglez.shop.kernel.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

  @Test
  void rejectsNegativePage() {
    assertThatThrownBy(() -> new Slice<>(List.of(), -1, 10, false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsSizeBelowOne() {
    assertThatThrownBy(() -> new Slice<>(List.of(), 0, 0, false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullContent() {
    assertThatThrownBy(() -> new Slice<>(null, 0, 10, false))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void copiesContent() {
    java.util.List<Integer> source = new java.util.ArrayList<>(List.of(1));
    Slice<Integer> slice = new Slice<>(source, 0, 10, false);
    source.add(2);

    assertThat(slice.content()).containsExactly(1);
  }
}
