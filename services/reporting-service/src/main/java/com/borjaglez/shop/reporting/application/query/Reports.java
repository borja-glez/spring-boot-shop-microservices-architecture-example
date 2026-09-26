package com.borjaglez.shop.reporting.application.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.shop.reporting.domain.ReportLine;
import com.borjaglez.shop.reporting.domain.ReportOrder;
import com.borjaglez.shop.reporting.domain.ReportStatus;
import com.borjaglez.specrepository.core.QueryPlan;

import lombok.Getter;

/**
 * The reports of the backoffice. Each query carries the client's filters as a plan; the handler
 * adds the conditions and the grouping, so a client can narrow a report but never change what it
 * counts.
 */
public final class Reports {

  private Reports() {}

  /** Orders by status. Answered with a {@code List<StatusCount>}. */
  @Getter
  public static class SummaryQuery extends Query {
    private final QueryPlan<ReportOrder> plan;

    public SummaryQuery(QueryPlan<ReportOrder> plan) {
      this.plan = Objects.requireNonNull(plan, "plan must not be null");
    }
  }

  /** Confirmed sales per day. Answered with a {@code List<DailySales>}. */
  @Getter
  public static class SalesByDayQuery extends Query {
    private final QueryPlan<ReportOrder> plan;

    public SalesByDayQuery(QueryPlan<ReportOrder> plan) {
      this.plan = Objects.requireNonNull(plan, "plan must not be null");
    }
  }

  /**
   * Products of confirmed orders that sold at least {@code minUnits}. Answered with a {@code
   * List<ProductSales>}.
   */
  @Getter
  public static class TopProductsQuery extends Query {
    private final QueryPlan<ReportLine> plan;
    private final int minUnits;

    public TopProductsQuery(QueryPlan<ReportLine> plan, int minUnits) {
      this.plan = Objects.requireNonNull(plan, "plan must not be null");
      this.minUnits = minUnits;
    }
  }

  /** Rejected orders by reason. Answered with a {@code List<RejectionCount>}. */
  @Getter
  public static class RejectionsQuery extends Query {
    private final QueryPlan<ReportOrder> plan;

    public RejectionsQuery(QueryPlan<ReportOrder> plan) {
      this.plan = Objects.requireNonNull(plan, "plan must not be null");
    }
  }

  /** Confirmed spending per customer. Answered with a {@code List<CustomerSales>}. */
  @Getter
  public static class CustomersQuery extends Query {
    private final QueryPlan<ReportOrder> plan;

    public CustomersQuery(QueryPlan<ReportOrder> plan) {
      this.plan = Objects.requireNonNull(plan, "plan must not be null");
    }
  }

  public record StatusCount(ReportStatus status, long orders) {}

  public record DailySales(
      LocalDate day, String currency, long orders, long customers, BigDecimal revenue) {}

  public record ProductSales(
      String sku, String name, String currency, long units, long orders, BigDecimal revenue) {}

  public record RejectionCount(String reason, long orders) {}

  public record CustomerSales(String customerId, String currency, long orders, BigDecimal spent) {}
}
