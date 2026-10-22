package com.borjaglez.shop.kernel.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class PageTest {

  @Test
  void noElementsMeansNoPages() {
    assertThat(new Page<>(List.of(), 0, 10, 0).totalPages()).isZero();
  }

  @Test
  void exactMultipleDoesNotAddAPage() {
    assertThat(new Page<>(List.of(1), 0, 10, 20).totalPages()).isEqualTo(2);
  }

  @Test
  void remainderAddsAPage() {
    assertThat(new Page<>(List.of(1), 0, 10, 21).totalPages()).isEqualTo(3);
  }

  @Test
  void mapTransformsContentAndKeepsPaging() {
    Page<String> mapped = new Page<>(List.of(1, 2), 1, 2, 5).map(n -> "n" + n);

    assertThat(mapped.content()).containsExactly("n1", "n2");
    assertThat(mapped.page()).isEqualTo(1);
    assertThat(mapped.size()).isEqualTo(2);
    assertThat(mapped.totalElements()).isEqualTo(5);
  }

  @Test
  void rejectsNegativePage() {
    assertThatThrownBy(() -> new Page<>(List.of(), -1, 10, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsSizeBelowOne() {
    assertThatThrownBy(() -> new Page<>(List.of(), 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNegativeTotalElements() {
    assertThatThrownBy(() -> new Page<>(List.of(), 0, 10, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullContent() {
    assertThatThrownBy(() -> new Page<>(null, 0, 10, 0)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void copiesContent() {
    java.util.List<Integer> source = new java.util.ArrayList<>(List.of(1));
    Page<Integer> page = new Page<>(source, 0, 10, 1);
    source.add(2);

    assertThat(page.content()).containsExactly(1);
  }
}
