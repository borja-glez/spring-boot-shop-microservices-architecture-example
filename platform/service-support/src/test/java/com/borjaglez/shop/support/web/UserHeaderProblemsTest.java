package com.borjaglez.shop.support.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

  @Test
  void otherMissingHeadersStayBadRequests() throws Exception {
    mvc.perform(get("/declared-other-header"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("bad-request"));
  }

  /**
   * As with {@code @CurrentUser}, the user comes first: a request without a valid user is answered
   * with the user problem whatever else its arguments get wrong, an unreadable body or a malformed
   * path variable included. Only the rejections made while the request is mapped to a handler come
   * before it ({@link UserHeaderOnInterfaceTest}).
   */
  @ParameterizedTest(name = "{0} user, {1}")
  @CsvSource({
    "missing, unreadable body, 401, missing-user",
    "missing, malformed id, 401, missing-user",
    "blank, unreadable body, 401, missing-user",
    "blank, malformed id, 401, missing-user",
    "blank, invalid body, 401, missing-user",
    "invalid, unreadable body, 400, invalid-user",
    "invalid, malformed id, 400, invalid-user",
    "invalid, invalid body, 400, invalid-user",
    "valid, unreadable body, 400, bad-request",
    "valid, malformed id, 400, bad-request",
    "valid, invalid body, 400, validation-failed"
  })
  void theUserIsCheckedBeforeTheRestOfTheRequest(
      String user, String problem, int status, String code) throws Exception {
    MockHttpServletRequestBuilder request =
        switch (problem) {
          case "unreadable body" ->
              post("/declared-user/{id}", "8a9c2c1e-6f59-4a44-9d4b-0f6f4e3f1c11")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{");
          case "malformed id" ->
              post("/declared-user/not-a-uuid")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":\"Desk\",\"price\":1}");
          default ->
              post("/declared-user/{id}", "8a9c2c1e-6f59-4a44-9d4b-0f6f4e3f1c11")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":\"\",\"price\":-1}");
        };
    switch (user) {
      case "blank" -> request.header("X-Shop-User", " ");
      case "invalid" -> request.header("X-Shop-User", "ana; drop table");
      case "valid" -> request.header("X-Shop-User", "seller-ana");
      default -> {}
    }

    mvc.perform(request).andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
  }

  @Test
  void anOptionalUserHeaderMayBeAbsentButNotInvalid() throws Exception {
    mvc.perform(get("/declared-optional-user"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user").value("null"));
    mvc.perform(get("/declared-optional-user").header("X-Shop-User", "ana; drop table"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-user"));
  }
}
