package com.borjaglez.shop.payments.application.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.shop.payments.domain.PaymentStatus;
import com.borjaglez.shop.payments.domain.PaymentView;
import com.borjaglez.specrepository.core.QueryPlan;

import lombok.Getter;

/** Queries and views of the payments backoffice. */
public final class PaymentQueries {

  private PaymentQueries() {}

  /** Payments matching the client plan. Answered with a {@code Page<PaymentSummary>}. */
  @Getter
  public static class SearchPaymentsQuery extends Query {

    private final QueryPlan<PaymentView> plan;
    private final Pageable pageable;

    public SearchPaymentsQuery(QueryPlan<PaymentView> plan, Pageable pageable) {
      this.plan = Objects.requireNonNull(plan, "plan must not be null");
      this.pageable = Objects.requireNonNull(pageable, "pageable must not be null");
    }
  }

  /** The events of an order's payment. Answered with a {@code List<PaymentEvent>}. */
  @Getter
  public static class GetPaymentHistoryQuery extends Query {

    private final UUID orderId;

    public GetPaymentHistoryQuery(UUID orderId) {
      this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
    }
  }

  public record PaymentSummary(
      UUID orderId,
      UUID paymentId,
      String customerId,
      BigDecimal amount,
      String currency,
      PaymentStatus status,
      String reason,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}

  /**
   * One event of a payment's stream.
   *
   * @param event the event as stored, serialized with its concrete type
   */
  public record PaymentEvent(
      long version,
      String eventType,
      OffsetDateTime occurredAt,
      OffsetDateTime publishedAt,
      Object event) {}
}
