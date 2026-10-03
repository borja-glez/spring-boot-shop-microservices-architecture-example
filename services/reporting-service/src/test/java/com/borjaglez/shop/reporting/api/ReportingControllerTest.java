package com.borjaglez.shop.reporting.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.reporting.application.query.Reports.TopProductsQuery;
import com.borjaglez.shop.reporting.application.rebuild.RebuildService;
import com.borjaglez.specrepository.core.PredicateCondition;

@WebMvcTest(ReportingController.class)
class ReportingControllerTest {

  @Autowired MockMvc mvc;
  @MockitoBean QueryBus queries;
  @MockitoBean RebuildService rebuilds;

  @ParameterizedTest
  @ValueSource(strings = {"summary", "sales-by-day", "rejections", "customers"})
  void orderReportsFilterByDateAndCurrency(String report) throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(List.of());

    mvc.perform(
            get("/api/reporting/" + report)
                .param("filter", "placedDay:gte:2026-01-01")
                .param("filter", "currency:eq:EUR"))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"summary", "sales-by-day", "rejections", "customers"})
  void orderReportsRejectOtherFields(String report) throws Exception {
    mvc.perform(get("/api/reporting/" + report).param("filter", "customerId:eq:cliente-lucia"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"));
    verifyNoInteractions(queries);
  }

  @ParameterizedTest
  @ValueSource(strings = {"summary", "sales-by-day", "rejections", "customers"})
  void orderReportsCannotBeSorted(String report) throws Exception {
    mvc.perform(get("/api/reporting/" + report).param("sort", "placedAt,desc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"));
    verifyNoInteractions(queries);
  }

  @Test
  void topProductsFilterByProductAndOrderDate() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(List.of());

    mvc.perform(
            get("/api/reporting/top-products")
                .param("filter", "sku:startswith:CAF")
                .param("filter", "order.placedDay:gte:2026-01-01")
                .param("minUnits", "3"))
        .andExpect(status().isOk());

    ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
    verify(queries).ask(captor.capture());
    TopProductsQuery query = (TopProductsQuery) captor.getValue();
    assertThat(query.getMinUnits()).isEqualTo(3);
    assertThat(query.getPlan().rootCondition().conditions())
        .extracting(c -> ((PredicateCondition) c).field())
        .containsExactly("sku", "order.placedDay");
  }

  @Test
  void topProductsCannotFilterOnTheAggregatedFields() throws Exception {
    mvc.perform(get("/api/reporting/top-products").param("filter", "quantity:gte:10"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"));
    verifyNoInteractions(queries);
  }

  @Test
  void topProductsRejectAValueAboveTheLimit() throws Exception {
    mvc.perform(
            get("/api/reporting/top-products").param("filter", "name:contains:" + "x".repeat(201)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"))
        .andExpect(jsonPath("$.detail").value(containsString("value too long (max 200")));
    verifyNoInteractions(queries);
  }

  @Test
  void reportsRejectOperatorsTheyDoNotOffer() throws Exception {
    mvc.perform(get("/api/reporting/summary").param("filter", "currency:neq:EUR"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-filter"));
    verifyNoInteractions(queries);
  }
}
