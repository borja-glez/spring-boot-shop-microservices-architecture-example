package com.borjaglez.shop.support.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.shop.support.web.problem.UserHeaderProblemMapper;

/**
 * Without {@link UserHeaderInterceptor}, in a standalone MockMvc for instance, the problems of a
 * declared user header still come out as {@code missing-user} and {@code invalid-user}: {@link
 * UserHeaderProblemMapper} finds the header among the binding and validation errors.
 */
class UserHeaderWithoutInterceptorTest {

  private final MockMvc mvc =
      standaloneSetup(new SampleController())
          .setControllerAdvice(
              new ProblemDetailsExceptionHandler(List.of(new UserHeaderProblemMapper())))
          .build();

  @Test
  void aMissingOrBlankUserHeaderIsAMissingUser() throws Exception {
    mvc.perform(get("/declared-user"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("missing-user"));
    mvc.perform(get("/declared-user").header("X-Shop-User", " "))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("missing-user"));
  }

  @Test
  void anInvalidUserHeaderIsAnInvalidUser() throws Exception {
    mvc.perform(get("/declared-user").header("X-Shop-User", "ana; drop table"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-user"));
  }

  @Test
  void otherHeadersAndParametersKeepTheirProblems() throws Exception {
    mvc.perform(get("/declared-other-header"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("bad-request"));
    mvc.perform(get("/declared-user-sized").header("X-Shop-User", "seller-ana").param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors[0].field").value("size"));
  }
}
