package com.borjaglez.shop.support.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.shop.support.autoconfigure.ShopChaosAutoConfiguration;
import com.borjaglez.shop.support.autoconfigure.ShopWebAutoConfiguration;

/** Without {@code shop.chaos.enabled} nobody can turn a fault on. */
@WebMvcTest(controllers = SampleController.class)
@ImportAutoConfiguration({ShopWebAutoConfiguration.class, ShopChaosAutoConfiguration.class})
@TestPropertySource(properties = "shop.chaos.service=payments")
class ChaosDisabledTest {

  @Autowired MockMvc mvc;

  @Test
  void theChaosEndpointAnswersNotFound() throws Exception {
    mvc.perform(get("/api/payments/chaos"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("chaos-disabled"));
    mvc.perform(
            put("/api/payments/chaos/x")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":1}"))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/payments/chaos")).andExpect(status().isNotFound());
  }
}
