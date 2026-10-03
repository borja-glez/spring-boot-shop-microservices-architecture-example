package com.borjaglez.shop.support.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.specrepository.http.spring.HttpFilterProperties;

/**
 * The shop defaults bound the HTTP filter syntax ({@code specrepository.http.*}) more tightly than
 * the library, and a request above a bound is a 400 {@code invalid-filter} before any SQL runs.
 */
@WebMvcTest(controllers = SampleController.class)
class HttpFilterLimitsTest {

  @Autowired MockMvc mvc;
  @Autowired ApplicationContext context;

  @Test
  void theShopDefaultsConfigureTheParser() {
    HttpFilterProperties properties = context.getBean(HttpFilterProperties.class);

    assertThat(properties.getMaxFilters()).isEqualTo(10);
    assertThat(properties.getMaxSortFields()).isEqualTo(3);
    assertThat(properties.getMaxValuesPerFilter()).isEqualTo(50);
    assertThat(properties.getMaxValueLength()).isEqualTo(200);
    assertThat(properties.getAllowedOperators())
        .containsExactlyInAnyOrder(
            "eq", "neq", "in", "notin", "gt", "gte", "lt", "lte", "between", "isnull", "isnotnull");
    assertThat(properties.getProblemDetails().isEnabled()).isFalse();
  }

  @Test
  void anInListUpToTheLimitIsAccepted() throws Exception {
    mvc.perform(get("/items").param("filter", "id:in:" + values(50)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conditions").value(1));
  }

  @Test
  void anInListAboveTheLimitIs400() throws Exception {
    mvc.perform(get("/items").param("filter", "id:in:" + values(51)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.detail").value(containsString("too many values (max 50)")))
        .andExpect(jsonPath("$.detail").value(containsString("'id'")));
  }

  @Test
  void aValueAboveTheLimitIs400WithoutEchoingIt() throws Exception {
    String longName = "x".repeat(201);
    mvc.perform(get("/items").param("filter", "name:eq:" + longName))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.detail").value(containsString("value too long (max 200")))
        .andExpect(content().string(not(containsString(longName))));
  }

  @Test
  void tooManyFiltersAre400() throws Exception {
    String[] filters = IntStream.range(0, 11).mapToObj(i -> "id:eq:" + i).toArray(String[]::new);
    mvc.perform(get("/items").param("filter", filters))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"));
  }

  @Test
  void tooManySortFieldsAre400() throws Exception {
    mvc.perform(get("/items").param("sort", "name,asc", "name,desc", "name,asc", "name,desc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"));
  }

  @Test
  void aDisallowedFieldNamesTheField() throws Exception {
    mvc.perform(get("/items").param("filter", "secret:eq:1"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.field").value("secret"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void aDisallowedSortNamesTheField() throws Exception {
    mvc.perform(get("/items").param("sort", "id,asc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.field").value("id"));
  }

  @Test
  void anUnknownOperatorIs400() throws Exception {
    mvc.perform(get("/items").param("filter", "name:like:cafe"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.detail").value(containsString("like")));
  }

  @Test
  void anOperatorOutsideTheDefaultListIs400() throws Exception {
    // Text search is not in the shop's default list; a service that needs it adds it.
    mvc.perform(get("/items").param("filter", "name:contains:cafe"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.detail").value(containsString("contains")));
  }

  @Test
  void theLibraryAdviceIsOffSoEveryErrorHasTheShopShape() {
    assertThat(context.containsBean("httpFilterProblemDetailsExceptionHandler")).isFalse();
  }

  private static String values(int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(Integer::toString)
        .collect(Collectors.joining("|"));
  }
}
