package com.borjaglez.shop.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ExternalTraceHeadersFilterTest {

  @Test
  void theTraceOfTheClientDoesNotGetIn() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
    request.addHeader("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-00");
    request.addHeader("TraceState", "vendor=x");
    request.addHeader("baggage", "user=mallory");
    request.addHeader("X-Shop-User", "cliente-lucia");
    MockFilterChain chain = new MockFilterChain();

    new ExternalTraceHeadersFilter().doFilter(request, new MockHttpServletResponse(), chain);

    HttpServletRequest seen = (HttpServletRequest) chain.getRequest();
    assertThat(seen.getHeader("traceparent")).isNull();
    assertThat(Collections.list(seen.getHeaders("tracestate"))).isEmpty();
    assertThat(Collections.list(seen.getHeaderNames())).containsExactly("X-Shop-User");
    assertThat(seen.getHeader("X-Shop-User")).isEqualTo("cliente-lucia");
    assertThat(Collections.list(seen.getHeaders("X-Shop-User"))).containsExactly("cliente-lucia");
  }
}
