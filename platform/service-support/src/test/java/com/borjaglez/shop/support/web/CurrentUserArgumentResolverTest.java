package com.borjaglez.shop.support.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.shop.support.autoconfigure.ShopWebAutoConfiguration;

@WebMvcTest(controllers = SampleController.class)
@ImportAutoConfiguration(ShopWebAutoConfiguration.class)
class CurrentUserArgumentResolverTest {

  @Autowired MockMvc mvc;

  @Test
  void userHeaderIsInjected() throws Exception {
    mvc.perform(get("/whoami").header(CurrentUserArgumentResolver.HEADER, "seller-ana"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user").value("seller-ana"));
  }

  @Test
  void missingRequiredUserIs401() throws Exception {
    mvc.perform(get("/whoami"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("missing-user"));
  }

  @Test
  void invalidUserIs400() throws Exception {
    mvc.perform(get("/whoami").header(CurrentUserArgumentResolver.HEADER, "ana; drop table"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-user"));
  }

  @Test
  void optionalUserMayBeAbsent() throws Exception {
    mvc.perform(get("/whoami-optional"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user").value("null"));
  }

  @SuppressWarnings("unused")
  void handler(@CurrentUser String user, @CurrentUser Integer wrongType, String plain) {}

  @Test
  void onlyStringParametersAnnotatedWithCurrentUserAreResolved() throws Exception {
    Method method =
        CurrentUserArgumentResolverTest.class.getDeclaredMethod(
            "handler", String.class, Integer.class, String.class);
    var resolver = new CurrentUserArgumentResolver();

    assertThat(resolver.supportsParameter(new MethodParameter(method, 0))).isTrue();
    assertThat(resolver.supportsParameter(new MethodParameter(method, 1))).isFalse();
    assertThat(resolver.supportsParameter(new MethodParameter(method, 2))).isFalse();
  }

  @Test
  void blankUserIsMissing() throws Exception {
    mvc.perform(get("/whoami").header(CurrentUserArgumentResolver.HEADER, " "))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("missing-user"));
  }
}
