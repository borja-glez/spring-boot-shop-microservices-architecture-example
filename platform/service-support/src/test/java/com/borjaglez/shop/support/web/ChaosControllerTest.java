package com.borjaglez.shop.support.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

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
import com.borjaglez.shop.testsupport.contract.OpenApiContract;

@WebMvcTest(controllers = SampleController.class)
@ImportAutoConfiguration({ShopWebAutoConfiguration.class, ShopChaosAutoConfiguration.class})
@TestPropertySource(
    properties = {
      "shop.chaos.enabled=true",
      "shop.chaos.service=payments",
      "shop.chaos.messages=AuthorizePayment"
    })
class ChaosControllerTest {

  private static final OpenApiContract CONTRACT =
      OpenApiContract.of(Path.of("src/main/openapi/chaos-api.v1.yaml"));

  @Autowired MockMvc mvc;
  @Autowired Chaos chaos;

  @BeforeEach
  void reset() {
    chaos.reset();
  }

  @Test
  void listsTheFaultsOfTheService() throws Exception {
    mvc.perform(get("/api/v1/chaos/payments"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches())
        .andExpect(jsonPath("$[0].key").value("AuthorizePayment.delay-ms"))
        .andExpect(jsonPath("$[0].kind").value("DELAY"))
        .andExpect(jsonPath("$[1].key").value("AuthorizePayment.fail"))
        .andExpect(jsonPath("$[1].value").value(0));
  }

  @Test
  void turnsAFaultOnAndEverythingOff() throws Exception {
    mvc.perform(
            put("/api/v1/chaos/payments/AuthorizePayment.fail")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":1}"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches())
        .andExpect(jsonPath("$.value").value(1));

    mvc.perform(delete("/api/v1/chaos/payments"))
        .andExpect(status().isNoContent())
        .andExpect(CONTRACT.matches());
    mvc.perform(get("/api/v1/chaos/payments"))
        .andExpect(CONTRACT.matches())
        .andExpect(jsonPath("$[1].value").value(0));
  }

  @Test
  void everythingOffAnswersAClientThatOnlyAcceptsJson() throws Exception {
    mvc.perform(delete("/api/v1/chaos/payments").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent())
        .andExpect(CONTRACT.matches());
    mvc.perform(delete("/api/v1/chaos/orders").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("chaos-disabled"))
        .andExpect(CONTRACT.matches());
  }

  @Test
  void rejectsUnknownFaultsAndBadValues() throws Exception {
    mvc.perform(
            put("/api/v1/chaos/payments/nope")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":1}"))
        .andExpect(status().isNotFound())
        .andExpect(CONTRACT.matches())
        .andExpect(jsonPath("$.code").value("chaos-fault-not-found"));
    mvc.perform(
            put("/api/v1/chaos/payments/AuthorizePayment.fail")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":7}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(CONTRACT.matches())
        .andExpect(jsonPath("$.code").value("chaos-value-out-of-range"));
  }

  @Test
  void aJsonOnlyClientGetsAnInvalidFaultChangeAsJson() throws Exception {
    mvc.perform(
            put("/api/v1/chaos/payments/AuthorizePayment.fail")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors[0].field").value("value"))
        .andExpect(CONTRACT.matchesResponse());
  }

  @Test
  void aServiceOtherThanTheConfiguredOneIsNotFound() throws Exception {
    mvc.perform(get("/api/v1/chaos/orders"))
        .andExpect(status().isNotFound())
        .andExpect(CONTRACT.matches())
        .andExpect(jsonPath("$.code").value("chaos-disabled"));
    mvc.perform(
            put("/api/v1/chaos/orders/AuthorizePayment.fail")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":1}"))
        .andExpect(status().isNotFound())
        .andExpect(CONTRACT.matches());
    mvc.perform(delete("/api/v1/chaos/orders"))
        .andExpect(status().isNotFound())
        .andExpect(CONTRACT.matches());
  }
}
