package com.borjaglez.shop.reporting.application.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.reporting.application.query.Reports.CustomerSales;
import com.borjaglez.shop.reporting.application.query.Reports.CustomersQuery;
import com.borjaglez.shop.reporting.application.query.Reports.DailySales;
import com.borjaglez.shop.reporting.application.query.Reports.ProductSales;
import com.borjaglez.shop.reporting.application.query.Reports.RejectionCount;
import com.borjaglez.shop.reporting.application.query.Reports.RejectionsQuery;
import com.borjaglez.shop.reporting.application.query.Reports.SalesByDayQuery;
import com.borjaglez.shop.reporting.application.query.Reports.StatusCount;
import com.borjaglez.shop.reporting.application.query.Reports.SummaryQuery;
import com.borjaglez.shop.reporting.application.query.Reports.TopProductsQuery;
import com.borjaglez.shop.reporting.domain.ReportLineRepository;
import com.borjaglez.shop.reporting.domain.ReportOrder;
import com.borjaglez.shop.reporting.domain.ReportOrderRepository;
import com.borjaglez.shop.reporting.domain.ReportStatus;
import com.borjaglez.specrepository.core.AggregateFunction;
import com.borjaglez.specrepository.core.GroupedRow;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.jpa.SpecificationExecutableQuery;

/**
 * Answers the reports with grouped queries of specification-repository.
 *
 * <p>Amounts are only added up within one currency: every money report groups by it too.
 *
 * <p>The client decides which rows (its filters), the server decides what is grouped and summed.
 * Each report derives the client plan with {@code repository.query(plan)}: the conditions it adds,
 * such as {@code status = CONFIRMED}, are server conditions, which the client's whitelist does not
 * restrict and a client {@code orFilter} cannot widen; grouping and aggregates are always the
 * server's choice. The rows come back as {@link GroupedRow}s ({@code findRows()}).
 */
@QueryHandler
public class ReportsHandler {

  /**
   * Every report runs the client's filters over the whole history: a combination the indexes do not
   * cover must not hold a connection for long. Spring applies the transaction timeout to every JPA
   * query run in it.
   */
  static final int REPORT_TIMEOUT_SECONDS = 10;

  private final ReportOrderRepository orders;
  private final ReportLineRepository lines;

  public ReportsHandler(ReportOrderRepository orders, ReportLineRepository lines) {
    this.orders = orders;
    this.lines = lines;
  }

  @HandleQuery
  @Transactional(readOnly = true, timeout = REPORT_TIMEOUT_SECONDS)
  public List<StatusCount> summary(SummaryQuery query) {
    return placed(query.getPlan())
        .groupBy("status")
        .select("status")
        .countAs("orders", "orderId")
        .findRows()
        .stream()
        .map(r -> new StatusCount((ReportStatus) r.get("status"), count(r, "orders")))
        .sorted(Comparator.comparing(StatusCount::status))
        .toList();
  }

  @HandleQuery
  @Transactional(readOnly = true, timeout = REPORT_TIMEOUT_SECONDS)
  public List<DailySales> salesByDay(SalesByDayQuery query) {
    return placed(query.getPlan(), ReportStatus.CONFIRMED)
        .groupBy("placedDay", "currency")
        .select("placedDay", "currency")
        .countAs("orders", "orderId")
        .countDistinctAs("customers", "customerId")
        .sumAs("revenue", "total")
        .findRows()
        .stream()
        .map(
            r ->
                new DailySales(
                    (LocalDate) r.get("placedDay"),
                    (String) r.get("currency"),
                    count(r, "orders"),
                    count(r, "customers"),
                    money(r, "revenue")))
        .sorted(Comparator.comparing(DailySales::day))
        .toList();
  }

  /**
   * Products of confirmed orders that sold at least {@code minUnits}.
   *
   * <p>The threshold is a {@code having} on {@code SUM(quantity)}, and {@code quantity} is not a
   * field the client may filter by. The derived query keeps the client's whitelist: its filters are
   * checked against it, while the {@code having}, like the server conditions, the grouping and the
   * aggregates, is server input and is not (the HTTP syntax has no {@code having}).
   */
  @HandleQuery
  @Transactional(readOnly = true, timeout = REPORT_TIMEOUT_SECONDS)
  public List<ProductSales> topProducts(TopProductsQuery query) {
    return lines
        .query(query.getPlan())
        .where("order.status", Operators.EQUALS, ReportStatus.CONFIRMED)
        .groupBy("sku", "name", "order.currency")
        .select("sku", "name", "order.currency")
        .sumAs("units", "quantity")
        .countDistinctAs("orders", "order.orderId")
        .sumAs("revenue", "revenue")
        .having(
            AggregateFunction.SUM,
            "quantity",
            Operators.GREATER_THAN_OR_EQUAL,
            (long) Math.max(query.getMinUnits(), 1))
        .findRows()
        .stream()
        .map(
            r ->
                new ProductSales(
                    (String) r.get("sku"),
                    (String) r.get("name"),
                    (String) r.get("order.currency"),
                    count(r, "units"),
                    count(r, "orders"),
                    money(r, "revenue")))
        .sorted(
            Comparator.comparingLong(ProductSales::units)
                .reversed()
                .thenComparing(ProductSales::sku))
        .toList();
  }

  @HandleQuery
  @Transactional(readOnly = true, timeout = REPORT_TIMEOUT_SECONDS)
  public List<RejectionCount> rejections(RejectionsQuery query) {
    return placed(query.getPlan(), ReportStatus.REJECTED)
        .groupBy("rejectionReason")
        .select("rejectionReason")
        .countAs("orders", "orderId")
        .findRows()
        .stream()
        .map(r -> new RejectionCount((String) r.get("rejectionReason"), count(r, "orders")))
        .sorted(Comparator.comparingLong(RejectionCount::orders).reversed())
        .toList();
  }

  @HandleQuery
  @Transactional(readOnly = true, timeout = REPORT_TIMEOUT_SECONDS)
  public List<CustomerSales> customers(CustomersQuery query) {
    return placed(query.getPlan(), ReportStatus.CONFIRMED)
        .groupBy("customerId", "currency")
        .select("customerId", "currency")
        .countAs("orders", "orderId")
        .sumAs("spent", "total")
        .findRows()
        .stream()
        .map(
            r ->
                new CustomerSales(
                    (String) r.get("customerId"),
                    (String) r.get("currency"),
                    count(r, "orders"),
                    money(r, "spent")))
        .sorted(Comparator.comparing(CustomerSales::spent).reversed())
        .toList();
  }

  /** The client's rows among the orders that were placed. */
  private SpecificationExecutableQuery<ReportOrder> placed(QueryPlan<ReportOrder> clientPlan) {
    return orders.query(clientPlan).where("placedAt", Operators.IS_NOT_NULL, null);
  }

  /** The client's rows among the placed orders with the given status. */
  private SpecificationExecutableQuery<ReportOrder> placed(
      QueryPlan<ReportOrder> clientPlan, ReportStatus status) {
    return placed(clientPlan).where("status", Operators.EQUALS, status);
  }

  private static long count(GroupedRow row, String alias) {
    Object value = row.get(alias);
    return value == null ? 0 : ((Number) value).longValue();
  }

  private static BigDecimal money(GroupedRow row, String alias) {
    Object value = row.get(alias);
    return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
  }
}
