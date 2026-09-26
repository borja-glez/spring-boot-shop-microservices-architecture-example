package com.borjaglez.shop.support.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.shop.support.autoconfigure.ShopWebAutoConfiguration;

@WebMvcTest(controllers = SampleController.class)
@ImportAutoConfiguration(ShopWebAutoConfiguration.class)
class ShopDefaultsTest {

  @Autowired MockMvc mvc;

  @Test
  void pageSizeIsCappedAtOneHundred() throws Exception {
    mvc.perform(get("/page").param("size", "10000")).andExpect(jsonPath("$.size").value(100));
  }

  @Test
  void negativePageFallsBackToFirstPage() throws Exception {
    mvc.perform(get("/page").param("page", "-1")).andExpect(jsonPath("$.page").value(0));
  }
}
