package com.borjaglez.shop.payments.application.query;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.eskit.EventStore;
import com.borjaglez.shop.payments.application.query.PaymentQueries.GetPaymentHistoryQuery;
import com.borjaglez.shop.payments.application.query.PaymentQueries.PaymentEvent;
import com.borjaglez.shop.payments.application.query.PaymentQueries.PaymentSummary;
import com.borjaglez.shop.payments.application.query.PaymentQueries.SearchPaymentsQuery;
import com.borjaglez.shop.payments.domain.Payment;
import com.borjaglez.shop.payments.domain.PaymentViewRepository;
import com.borjaglez.shop.support.error.NotFoundException;

/** Read side of the payments backoffice. Every read uses specification-repository. */
@QueryHandler
public class PaymentQueryHandler {

  /**
   * Last updated first, then by order: a server sort, so it may break ties with {@code orderId},
   * which the client can filter by but not sort by.
   */
  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("orderId"));

  /**
   * The lists take the client's filters: a combination the indexes do not cover must not hold a
   * connection for long. Spring applies the transaction timeout to every JPA query run in it.
   */
  static final int LIST_TIMEOUT_SECONDS = 5;

  /**
   * The list reads its columns straight into {@link PaymentSummary}, in constructor order, without
   * loading entities ({@code selectInto}). Hibernate builds each row with the record's constructor.
   */
  private static final String[] SUMMARY_FIELDS = {
    "orderId",
    "paymentId",
    "customerId",
    "amount",
    "currency",
    "status",
    "reason",
    "createdAt",
    "updatedAt"
  };

  private final PaymentViewRepository views;
  private final EventStore eventStore;

  public PaymentQueryHandler(PaymentViewRepository views, EventStore eventStore) {
    this.views = views;
    this.eventStore = eventStore;
  }

  @HandleQuery
  @Transactional(readOnly = true, timeout = LIST_TIMEOUT_SECONDS)
  public Page<PaymentSummary> payments(SearchPaymentsQuery query) {
    return views
        .query(query.getPlan())
        .sortedByDefault(NEWEST_FIRST)
        .select(SUMMARY_FIELDS)
        .selectInto(PaymentSummary.class)
        .findAll(query.getPageable());
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public List<PaymentEvent> history(GetPaymentHistoryQuery query) {
    List<PaymentEvent> events =
        eventStore.load(Payment.STREAM_TYPE, query.getOrderId().toString()).stream()
            .map(
                recorded ->
                    new PaymentEvent(
                        recorded.version(),
                        recorded.eventType(),
                        recorded.occurredAt(),
                        recorded.publishedAt(),
                        recorded.event()))
            .toList();
    if (events.isEmpty()) {
      throw new NotFoundException(
          "payment-not-found", "No payment was requested for order " + query.getOrderId());
    }
    return events;
  }
}
