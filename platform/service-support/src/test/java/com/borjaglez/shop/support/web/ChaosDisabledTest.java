package com.borjaglez.shop.support.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.shop.support.autoconfigure.ShopChaosAutoConfiguration;
import com.borjaglez.shop.support.autoconfigure.ShopWebAutoConfiguration;
import com.borjaglez.shop.testsupport.contract.OpenApiContract;

/** Without {@code shop.chaos.enabled} nobody can turn a fault on. */
@WebMvcTest(controllers = SampleController.class)
@ImportAutoConfiguration({ShopWebAutoConfiguration.class, ShopChaosAutoConfiguration.class})
@TestPropertySource(properties = "shop.chaos.service=payments")
class ChaosDisabledTest {

  private static final OpenApiContract CONTRACT =
      OpenApiContract.of(Path.of("src/main/openapi/chaos-api.v1.yaml"));

  @Autowired MockMvc mvc;

  @Test
  void theChaosEndpointAnswersNotFound() throws Exception {
    mvc.perform(get("/api/v1/chaos/payments"))
        .andExpect(status().isNotFound())
        .andExpect(CONTRACT.matches())
        .andExpect(jsonPath("$.code").value("chaos-disabled"));
    mvc.perform(
            put("/api/v1/chaos/payments/x")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":1}"))
        .andExpect(status().isNotFound())
        .andExpect(CONTRACT.matches());
    mvc.perform(delete("/api/v1/chaos/payments"))
        .andExpect(status().isNotFound())
        .andExpect(CONTRACT.matches());
  }
}
