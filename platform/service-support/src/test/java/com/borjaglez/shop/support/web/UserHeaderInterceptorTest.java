package com.borjaglez.shop.support.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class UserHeaderInterceptorTest {

  @Test
  void aHandlerThatIsNotAMethodIsLetThrough() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/static/app.js");

    assertThat(
            new UserHeaderInterceptor()
                .preHandle(request, new MockHttpServletResponse(), new Object()))
        .isTrue();
  }
}
