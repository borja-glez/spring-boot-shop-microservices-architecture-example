package com.borjaglez.shop.support.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Services write plain {@code @WebMvcTest}s; the shop web conventions must be part of that slice
 * without importing anything.
 */
@WebMvcTest(controllers = SampleController.class)
class WebMvcSliceTest {

  @Autowired MockMvc mvc;

  @Test
  void problemDetailsAndCorrelationAreActiveInTheSlice() throws Exception {
    mvc.perform(get("/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("product-not-found"))
        .andExpect(header().exists(CorrelationIdFilter.HEADER));
  }

  @Test
  void currentUserResolverIsActiveInTheSlice() throws Exception {
    mvc.perform(get("/whoami")).andExpect(status().isUnauthorized());
  }
}
