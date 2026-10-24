package com.borjaglez.shop.kernel.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PageRequestTest {

  @Test
  void acceptsFirstPageOfSizeOne() {
    PageRequest request = new PageRequest(0, 1);

    assertThat(request.page()).isZero();
    assertThat(request.size()).isEqualTo(1);
  }

  @Test
  void rejectsNegativePage() {
    assertThatThrownBy(() -> new PageRequest(-1, 10)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsSizeBelowOne() {
    assertThatThrownBy(() -> new PageRequest(0, 0)).isInstanceOf(IllegalArgumentException.class);
  }
}
