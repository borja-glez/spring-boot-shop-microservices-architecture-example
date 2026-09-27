package com.borjaglez.shop.payments.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.contracts.payments.PaymentAuthorized;
import com.borjaglez.shop.payments.application.query.PaymentQueries.GetPaymentHistoryQuery;
import com.borjaglez.shop.payments.application.query.PaymentQueries.PaymentEvent;
import com.borjaglez.shop.payments.application.query.PaymentQueries.PaymentSummary;
import com.borjaglez.shop.payments.application.query.PaymentQueries.SearchPaymentsQuery;
import com.borjaglez.shop.payments.domain.PaymentStatus;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

  private static final UUID ORDER = UUID.fromString("5b0f6c2e-1d3a-4c55-9e0b-7a1f2c3d4e5f");
  private static final UUID PAYMENT = UUID.fromString("8a9c2c1e-6f59-4a44-9d4b-0f6f4e3f1c11");
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-25T10:00:00Z");

  @Autowired MockMvc mvc;
  @MockitoBean QueryBus queries;

  @Test
  void paymentsAreSearchedWithHttpFilters() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    new PaymentSummary(
                        ORDER,
                        PAYMENT,
                        "cliente-lucia",
                        new BigDecimal("450.00"),
                        "EUR",
                        PaymentStatus.DECLINED,
                        "card-limit-exceeded",
                        NOW,
                        NOW))));

    mvc.perform(
            get("/api/payments").param("filter", "status:eq:DECLINED").param("sort", "amount,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].reason").value("card-limit-exceeded"))
        .andExpect(jsonPath("$.content[0].status").value("DECLINED"));

    ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
    verify(queries).ask(captor.capture());
    assertThat(((SearchPaymentsQuery) captor.getValue()).getPlan().sort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, "amount"));
  }

  @Test
  void theHistoryShowsThePaymentEvents() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(
            List.of(
                new PaymentEvent(
                    1,
                    "shop.payments.1.event.payment.payment-authorized",
                    NOW,
                    NOW,
                    new PaymentAuthorized(
                        PAYMENT, ORDER, "cliente-lucia", new BigDecimal("80.00"), "EUR"))));

    mvc.perform(get("/api/payments/{orderId}/history", ORDER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].event.amount").value(80.0));

    ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
    verify(queries).ask(captor.capture());
    assertThat(((GetPaymentHistoryQuery) captor.getValue()).getOrderId()).isEqualTo(ORDER);
  }
}
