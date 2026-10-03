package com.borjaglez.shop.support.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

class SliceResponseTest {

  @Test
  void copiesContentAndPagingWithoutATotal() {
    var slice = new SliceImpl<>(List.of("a", "b"), PageRequest.of(1, 2), true);

    assertThat(SliceResponse.of(slice))
        .isEqualTo(new SliceResponse<>(List.of("a", "b"), 1, 2, true));
  }
}
