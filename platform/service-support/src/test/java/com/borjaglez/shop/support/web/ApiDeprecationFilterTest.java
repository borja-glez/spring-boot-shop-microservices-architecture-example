package com.borjaglez.shop.support.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.shop.support.autoconfigure.ShopWebAutoConfiguration;

class ApiDeprecationFilterTest {

  @WebMvcTest(controllers = SampleController.class)
  @ImportAutoConfiguration(ShopWebAutoConfiguration.class)
  @TestPropertySource(
      properties = {
        "shop.api.deprecations[0].method=GET",
        "shop.api.deprecations[0].path=/whoami-*",
        "shop.api.deprecations[0].deprecated-at=2026-01-15T00:00:00Z",
        "shop.api.deprecations[0].sunset=2026-07-01T00:00:00Z",
        "shop.api.deprecations[1].method=GET",
        "shop.api.deprecations[1].path=/things/**",
        "shop.api.deprecations[1].deprecated-at=2026-02-01T00:00:00Z",
        "shop.api.deprecations[1].successor=https://shop.example.com/api/v2/things",
        "shop.api.deprecations[2].method=GET",
        "shop.api.deprecations[2].path=/things/**",
        "shop.api.deprecations[2].deprecated-at=2026-03-01T00:00:00Z",
        "shop.api.deprecations[3].method=GET",
        "shop.api.deprecations[3].path=/declared-user",
        "shop.api.deprecations[3].deprecated-at=2026-01-15T00:00:00Z",
        "shop.api.deprecations[3].sunset=2026-12-25T01:30:00+02:00",
      })
  @Nested
  class Configured {

    @Autowired MockMvc mvc;

    @Test
    void addsDeprecationAndSunsetToMatchingRequests() throws Exception {
      mvc.perform(get("/whoami-optional"))
          .andExpect(status().isOk())
          .andExpect(header().string("Deprecation", "@1768435200"))
          .andExpect(header().string("Sunset", "Wed, 01 Jul 2026 00:00:00 GMT"))
          .andExpect(header().doesNotExist("Link"));
    }

    @Test
    void sunsetIsAnHttpDateInGmt() throws Exception {
      mvc.perform(get("/declared-user"))
          .andExpect(header().string("Sunset", "Thu, 24 Dec 2026 23:30:00 GMT"));
    }

    @Test
    void matchesTheDecodedPath() throws Exception {
      mvc.perform(get(URI.create("/whoami%2Doptional")))
          .andExpect(header().string("Deprecation", "@1768435200"));
    }

    @Test
    void appliesOnlyTheFirstMatchingEntry() throws Exception {
      mvc.perform(get("/things/42"))
          .andExpect(header().string("Deprecation", "@1769904000"))
          .andExpect(header().stringValues("Deprecation", "@1769904000"));
    }

    @Test
    void addsSuccessorLink() throws Exception {
      mvc.perform(get("/things/42"))
          .andExpect(header().string("Deprecation", "@1769904000"))
          .andExpect(
              header()
                  .string(
                      "Link",
                      "<https://shop.example.com/api/v2/things>; rel=\"successor-version\""))
          .andExpect(header().doesNotExist("Sunset"));
    }

    @Test
    void leavesOtherRequestsAlone() throws Exception {
      mvc.perform(get("/not-found"))
          .andExpect(header().doesNotExist("Deprecation"))
          .andExpect(header().doesNotExist("Sunset"))
          .andExpect(header().doesNotExist("Link"));
      mvc.perform(post("/things")).andExpect(header().doesNotExist("Deprecation"));
    }
  }

  @WebMvcTest(controllers = SampleController.class)
  @ImportAutoConfiguration(ShopWebAutoConfiguration.class)
  @Nested
  class Unconfigured {

    @Autowired MockMvc mvc;

    @Test
    void doesNothingWithoutConfiguration() throws Exception {
      mvc.perform(get("/whoami-optional"))
          .andExpect(status().isOk())
          .andExpect(header().doesNotExist("Deprecation"))
          .andExpect(header().doesNotExist("Sunset"))
          .andExpect(header().doesNotExist("Link"));
    }
  }
}
