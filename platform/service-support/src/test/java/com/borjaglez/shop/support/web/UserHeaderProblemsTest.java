package com.borjaglez.shop.support.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.shop.support.autoconfigure.ShopWebAutoConfiguration;

/**
 * The user header declared as a constrained {@code @RequestHeader}, as the interfaces generated
 * from the contracts do, is reported like {@code @CurrentUser} reports it.
 */
@WebMvcTest(controllers = SampleController.class)
@ImportAutoConfiguration(ShopWebAutoConfiguration.class)
class UserHeaderProblemsTest {

  @Autowired MockMvc mvc;

  @Test
  void missingUserHeaderIs401MissingUser() throws Exception {
    mvc.perform(get("/declared-user"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("missing-user"))
        .andExpect(jsonPath("$.type").value("https://shop.borjaglez.com/problems/missing-user"))
        .andExpect(jsonPath("$.detail").value("This operation needs the X-Shop-User header."))
        .andExpect(jsonPath("$.errors").doesNotExist());
    mvc.perform(get("/declared-user").header("X-Shop-User", " "))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("missing-user"));
  }

  @Test
  void invalidUserHeaderIs400InvalidUser() throws Exception {
    mvc.perform(get("/declared-user").header("X-Shop-User", "ana; drop table"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-user"))
        .andExpect(jsonPath("$.type").value("https://shop.borjaglez.com/problems/invalid-user"))
        .andExpect(
            jsonPath("$.detail").value("X-Shop-User must be 1-64 characters from [A-Za-z0-9._@-]."))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void validUserHeaderIsAccepted() throws Exception {
    mvc.perform(get("/declared-user").header("X-Shop-User", "seller-ana"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user").value("seller-ana"));
  }

  @Test
  void otherValidationErrorsStayValidationFailed() throws Exception {
    mvc.perform(get("/declared-user-sized").header("X-Shop-User", "seller-ana").param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("size"));
  }
}
