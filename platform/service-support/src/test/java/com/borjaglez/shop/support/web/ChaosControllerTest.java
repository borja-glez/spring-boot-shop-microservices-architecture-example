package com.borjaglez.shop.support.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.shop.support.autoconfigure.ShopChaosAutoConfiguration;
import com.borjaglez.shop.support.autoconfigure.ShopWebAutoConfiguration;
import com.borjaglez.shop.support.chaos.Chaos;

@WebMvcTest(controllers = SampleController.class)
@ImportAutoConfiguration({ShopWebAutoConfiguration.class, ShopChaosAutoConfiguration.class})
@TestPropertySource(
    properties = {
      "shop.chaos.enabled=true",
      "shop.chaos.service=payments",
      "shop.chaos.messages=AuthorizePayment"
    })
class ChaosControllerTest {

  @Autowired MockMvc mvc;
  @Autowired Chaos chaos;

  @BeforeEach
  void reset() {
    chaos.reset();
  }

  @Test
  void listsTheFaultsOfTheService() throws Exception {
    mvc.perform(get("/api/payments/chaos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].key").value("AuthorizePayment.delay-ms"))
        .andExpect(jsonPath("$[0].kind").value("DELAY"))
        .andExpect(jsonPath("$[1].key").value("AuthorizePayment.fail"))
        .andExpect(jsonPath("$[1].value").value(0));
  }

  @Test
  void turnsAFaultOnAndEverythingOff() throws Exception {
    mvc.perform(
            put("/api/payments/chaos/AuthorizePayment.fail")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.value").value(1));

    mvc.perform(delete("/api/payments/chaos")).andExpect(status().isNoContent());
    mvc.perform(get("/api/payments/chaos")).andExpect(jsonPath("$[1].value").value(0));
  }

  @Test
  void rejectsUnknownFaultsAndBadValues() throws Exception {
    mvc.perform(
            put("/api/payments/chaos/nope")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":1}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("chaos-fault-not-found"));
    mvc.perform(
            put("/api/payments/chaos/AuthorizePayment.fail")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":7}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("chaos-value-out-of-range"));
  }
}
