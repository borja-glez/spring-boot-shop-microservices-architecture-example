package com.borjaglez.shop.support.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.shop.support.autoconfigure.ShopWebAutoConfiguration;

@WebMvcTest(controllers = SampleController.class)
@ImportAutoConfiguration(ShopWebAutoConfiguration.class)
class CorrelationIdFilterTest {

  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  @Autowired MockMvc mvc;

  @Test
  void incomingCorrelationIdIsEchoedAndExposedToMdcAndMessageContext() throws Exception {
    mvc.perform(get("/correlation").header(CorrelationIdFilter.HEADER, "checkout-7f3a"))
        .andExpect(status().isOk())
        .andExpect(header().string(CorrelationIdFilter.HEADER, "checkout-7f3a"))
        .andExpect(jsonPath("$.messageContext").value("checkout-7f3a"))
        .andExpect(jsonPath("$.mdc").value("checkout-7f3a"));
  }

  @Test
  void missingCorrelationIdIsGenerated() throws Exception {
    mvc.perform(get("/correlation"))
        .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern(UUID_PATTERN)))
        .andExpect(jsonPath("$.messageContext").value(matchesPattern(UUID_PATTERN)));
  }

  @Test
  void unsafeCorrelationIdIsReplaced() throws Exception {
    mvc.perform(get("/correlation").header(CorrelationIdFilter.HEADER, "bad value<script>"))
        .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
    mvc.perform(get("/correlation").header(CorrelationIdFilter.HEADER, "x".repeat(65)))
        .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
  }

  @Test
  void contextIsClearedAfterTheRequest() throws Exception {
    mvc.perform(get("/correlation").header(CorrelationIdFilter.HEADER, "abc-123"));
    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    assertThat(MessageContext.current().isEmpty()).isTrue();
  }
}
