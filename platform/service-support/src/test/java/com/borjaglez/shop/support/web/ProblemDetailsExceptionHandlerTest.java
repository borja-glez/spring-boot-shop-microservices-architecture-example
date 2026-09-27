package com.borjaglez.shop.support.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.shop.support.autoconfigure.ShopWebAutoConfiguration;

@WebMvcTest(controllers = SampleController.class)
@ImportAutoConfiguration(ShopWebAutoConfiguration.class)
class ProblemDetailsExceptionHandlerTest {

  @Autowired MockMvc mvc;

  @Test
  void notFoundDomainExceptionBecomes404ProblemWithCode() throws Exception {
    mvc.perform(get("/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("product-not-found"))
        .andExpect(
            jsonPath("$.type").value("https://shop.borjaglez.com/problems/product-not-found"))
        .andExpect(jsonPath("$.detail").value("Product 42 does not exist"))
        .andExpect(jsonPath("$.instance").value("/not-found"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void conflictDomainExceptionBecomes409() throws Exception {
    mvc.perform(get("/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("duplicate-sku"));
  }

  @Test
  void businessRuleViolationBecomes422() throws Exception {
    mvc.perform(get("/rule"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("product-discontinued"));
  }

  @Test
  void domainExceptionWrappedByTheCommandBusIsUnwrapped() throws Exception {
    mvc.perform(get("/wrapped-domain"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("product-not-found"));
  }

  @Test
  void unexpectedExceptionBecomes500WithoutLeakingItsMessage() throws Exception {
    mvc.perform(get("/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("internal-error"))
        .andExpect(content().string(not(containsString("secret internal detail"))));
  }

  @Test
  void genericIllegalArgumentIsNotMaskedAsClientError() throws Exception {
    // Only the filter exceptions of the library are client errors; other ones stay 500s.
    mvc.perform(get("/illegal-argument")).andExpect(status().isInternalServerError());
  }

  @Test
  void filterSyntaxErrorBecomes400InvalidFilter() throws Exception {
    mvc.perform(get("/filter-syntax"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.detail").value(containsString("operator must not be empty")));
  }

  @Test
  void unknownFilterOperatorBecomes400InvalidFilter() throws Exception {
    mvc.perform(get("/filter-operator"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"));
  }

  @Test
  void disallowedFieldWrappedByRepositoryProxyBecomes400() throws Exception {
    mvc.perform(get("/disallowed-field"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.detail").value(containsString("seller.email")));
  }

  @Test
  void filterValueOfTheWrongTypeBecomes400InvalidFilter() throws Exception {
    mvc.perform(get("/filter-value"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.detail").value(containsString("abc")));
  }

  @Test
  void aFilterTheQueryEngineCannotRunBecomes400InvalidFilter() throws Exception {
    mvc.perform(get("/filter-operator-at-query-time"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.detail").value(containsString("like")));
  }

  @Test
  void optimisticLockFailureBecomes409ConcurrentModification() throws Exception {
    mvc.perform(get("/optimistic-lock"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("concurrent-modification"));
  }

  @Test
  void constraintViolationBecomes400ValidationFailed() throws Exception {
    mvc.perform(get("/constraint-violation"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"));
  }

  @Test
  void invalidRequestBodyListsFieldErrors() throws Exception {
    mvc.perform(
            post("/things")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"price\":-3}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[?(@.field == 'price')].message").exists());
  }

  @Test
  void frameworkErrorsAlsoCarryCodeAndCorrelationId() throws Exception {
    mvc.perform(post("/not-found"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("method-not-allowed"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty())
        .andExpect(header().exists(CorrelationIdFilter.HEADER));
  }

  @Test
  void malformedJsonBodyBecomes400() throws Exception {
    mvc.perform(post("/things").contentType(MediaType.APPLICATION_JSON).content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("bad-request"));
  }
}
