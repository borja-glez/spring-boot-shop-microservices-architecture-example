package com.borjaglez.shop.payments.api;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.payments.application.query.PaymentQueries.GetPaymentHistoryQuery;
import com.borjaglez.shop.payments.application.query.PaymentQueries.PaymentEvent;
import com.borjaglez.shop.payments.application.query.PaymentQueries.PaymentSummary;
import com.borjaglez.shop.payments.application.query.PaymentQueries.SearchPaymentsQuery;
import com.borjaglez.shop.payments.domain.PaymentView;
import com.borjaglez.shop.support.web.PageResponse;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.http.spring.FilterableQuery;

/**
 * Payments backoffice. Payments are only created by the checkout saga, so the API only reads. Like
 * the event store explorer, it is open to every demo user on purpose.
 */
@RestController
@RequestMapping("/api/payments")
class PaymentController {

  private final QueryBus queries;

  PaymentController(QueryBus queries) {
    this.queries = queries;
  }

  @GetMapping
  PageResponse<PaymentSummary> payments(
      @FilterableQuery(
              value = PaymentView.class,
              filterableFields = {
                "orderId",
                "customerId",
                "status",
                "reason",
                "amount",
                "currency",
                "updatedAt"
              },
              sortableFields = {"amount", "createdAt", "updatedAt"})
          QueryPlan<PaymentView> plan,
      Pageable pageable) {
    Page<PaymentSummary> page = queries.ask(new SearchPaymentsQuery(plan, pageable));
    return PageResponse.of(page);
  }

  @GetMapping("/{orderId}/history")
  List<PaymentEvent> history(@PathVariable UUID orderId) {
    return queries.ask(new GetPaymentHistoryQuery(orderId));
  }
}
