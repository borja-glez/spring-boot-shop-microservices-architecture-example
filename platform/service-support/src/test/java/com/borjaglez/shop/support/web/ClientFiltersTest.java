package com.borjaglez.shop.support.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.borjaglez.shop.kernel.query.ClientFilter;
import com.borjaglez.specrepository.http.HttpFilterParserConfiguration;

class ClientFiltersTest {

  private final ClientFilters defaults =
      new ClientFilters(HttpFilterParserConfiguration.defaults());

  @Test
  void readsEveryValueOfEachParameterWhole() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/things");
    request.addParameter("filter", "name:contains:sal, aceite", "price.amount:between:10,20");
    request.addParameter("orFilter", "city:eq:Madrid;tags:eq:a,b");
    request.addParameter("sort", "price.amount,desc");

    assertThat(defaults.from(request))
        .isEqualTo(
            new ClientFilter(
                List.of("name:contains:sal, aceite", "price.amount:between:10,20"),
                List.of("city:eq:Madrid;tags:eq:a,b"),
                List.of("price.amount,desc")));
  }

  @Test
  void keepsTheOrderOfRepeatedValues() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/things");
    request.addParameter("sort", "price.amount,desc", "name");

    assertThat(defaults.from(request).sorts()).containsExactly("price.amount,desc", "name");
  }

  @Test
  void aRequestWithoutFiltersHasNone() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/things");
    request.addParameter("page", "2");

    assertThat(defaults.from(request)).isEqualTo(ClientFilter.none());
  }

  @Test
  void readsTheParametersTheParserIsConfiguredWith() {
    ClientFilters renamed =
        new ClientFilters(
            HttpFilterParserConfiguration.builder()
                .filterParam("where")
                .orFilterParam("anyOf")
                .sortParam("order")
                .build());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/things");
    request.addParameter("where", "name:eq:a");
    request.addParameter("anyOf", "name:eq:b;name:eq:c");
    request.addParameter("order", "name,desc");
    request.addParameter("filter", "name:eq:ignored");
    request.addParameter("sort", "ignored");

    assertThat(renamed.from(request))
        .isEqualTo(
            new ClientFilter(
                List.of("name:eq:a"), List.of("name:eq:b;name:eq:c"), List.of("name,desc")));
  }
}
