package com.borjaglez.shop.support.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

  @Test
  void copiesContentAndPaging() {
    var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

    assertThat(PageResponse.of(page)).isEqualTo(new PageResponse<>(List.of("a", "b"), 1, 2, 5, 3));
  }
}
