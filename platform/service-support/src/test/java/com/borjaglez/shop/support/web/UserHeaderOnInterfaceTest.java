package com.borjaglez.shop.support.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.shop.support.autoconfigure.ShopWebAutoConfiguration;

/**
 * {@link UserHeaderInterceptor} finds the user header that an interface declares, as the interfaces
 * generated from the contracts do, and checks it before the rest of the request. The rejections
 * Spring MVC makes while it maps the request to a handler come before it.
 */
@WebMvcTest(controllers = SampleUserApiController.class)
@ImportAutoConfiguration(ShopWebAutoConfiguration.class)
class UserHeaderOnInterfaceTest {

  private static final String ID = "8a9c2c1e-6f59-4a44-9d4b-0f6f4e3f1c11";

  @Autowired MockMvc mvc;

  @Test
  void aBlankUserIsAnsweredBeforeAnUnreadableBody() throws Exception {
    mvc.perform(
            post("/api-user/{id}", ID)
                .header("X-Shop-User", " ")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("missing-user"));
  }

  @Test
  void anInvalidUserIsAnsweredBeforeAMalformedPathVariable() throws Exception {
    mvc.perform(
            post("/api-user/not-a-uuid")
                .header("X-Shop-User", "ana; drop table")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Desk\",\"price\":1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-user"));
  }

  @Test
  void aValidUserReachesTheController() throws Exception {
    mvc.perform(
            post("/api-user/{id}", ID)
                .header("X-Shop-User", "seller-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Desk\",\"price\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user").value("seller-ana"))
        .andExpect(jsonPath("$.name").value("Desk"));
  }

  /**
   * A content type the operation does not consume rules out every handler, so no handler checks the
   * user: the answer is 415 even without a user.
   */
  @Test
  void anUnsupportedContentTypeIsAnsweredBeforeTheUser() throws Exception {
    mvc.perform(post("/api-user/{id}", ID).contentType(MediaType.TEXT_PLAIN).content("name=Desk"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("unsupported-media-type"));
  }
}
