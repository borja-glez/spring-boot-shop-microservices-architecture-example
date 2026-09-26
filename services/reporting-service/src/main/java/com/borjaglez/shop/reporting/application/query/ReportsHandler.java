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
import com.borjaglez.shop.reporting.domain.ReportOrderRepository;
import com.borjaglez.shop.reporting.domain.ReportStatus;
import com.borjaglez.shop.support.query.QueryPlans;
import com.borjaglez.specrepository.core.AggregateFunction;
import com.borjaglez.specrepository.core.AggregateSelection;
import com.borjaglez.specrepository.core.FieldSelection;
import com.borjaglez.specrepository.core.GroupedRow;
import com.borjaglez.specrepository.core.HavingCondition;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryPlan;

/**
 * Answers the reports with grouped queries of specification-repository.
 *
 * <p>Amounts are only added up within one currency: every money report groups by it too.
 *
 * <p>Order matters when a plan is built: {@link QueryPlans#requiring} first, which checks the
 * client's filters against their whitelist and then allows every field, and only then {@link
 * QueryPlans#grouping}, whose grouping and aggregate fields are the server's choice.
 */
@QueryHandler
public class ReportsHandler {

  private static final PredicateCondition CONFIRMED =
      new PredicateCondition("status", Operators.EQUALS, ReportStatus.CONFIRMED, false, false);
  private static final PredicateCondition LINE_OF_CONFIRMED =
      new PredicateCondition(
          "order.status", Operators.EQUALS, ReportStatus.CONFIRMED, false, false);
  private static final PredicateCondition REJECTED =
      new PredicateCondition("status", Operators.EQUALS, ReportStatus.REJECTED, false, false);
  private static final PredicateCondition PLACED_ORDERS =
      new PredicateCondition("placedAt", Operators.IS_NOT_NULL, null, false, false);

  private final ReportOrderRepository orders;
  private final ReportLineRepository lines;

  public ReportsHandler(ReportOrderRepository orders, ReportLineRepository lines) {
    this.orders = orders;
    this.lines = lines;
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public List<StatusCount> summary(SummaryQuery query) {
    return orders
        .findAllGrouped(
            QueryPlans.grouping(
                QueryPlans.requiring(query.getPlan(), PLACED_ORDERS),
                List.of("status"),
                List.of(
                    new FieldSelection("status"),
                    new AggregateSelection(AggregateFunction.COUNT, "orderId", "orders"))))
        .stream()
        .map(r -> new StatusCount((ReportStatus) r.get("status"), count(r, "orders")))
        .sorted(Comparator.comparing(StatusCount::status))
        .toList();
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public List<DailySales> salesByDay(SalesByDayQuery query) {
    return orders
        .findAllGrouped(
            QueryPlans.grouping(
                QueryPlans.requiring(query.getPlan(), CONFIRMED, PLACED_ORDERS),
                List.of("placedDay", "currency"),
                List.of(
                    new FieldSelection("placedDay"),
                    new FieldSelection("currency"),
                    new AggregateSelection(AggregateFunction.COUNT, "orderId", "orders"),
                    new AggregateSelection(
                        AggregateFunction.COUNT_DISTINCT, "customerId", "customers"),
                    new AggregateSelection(AggregateFunction.SUM, "total", "revenue"))))
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

  @HandleQuery
  @Transactional(readOnly = true)
  public List<ProductSales> topProducts(TopProductsQuery query) {
    QueryPlan<com.borjaglez.shop.reporting.domain.ReportLine> plan =
        QueryPlans.grouping(
            QueryPlans.requiring(query.getPlan(), LINE_OF_CONFIRMED),
            List.of("sku", "name", "order.currency"),
            List.of(
                new FieldSelection("sku"),
                new FieldSelection("name"),
                new FieldSelection("order.currency"),
                new AggregateSelection(AggregateFunction.SUM, "quantity", "units"),
                new AggregateSelection(AggregateFunction.COUNT_DISTINCT, "order.orderId", "orders"),
                new AggregateSelection(AggregateFunction.SUM, "revenue", "revenue")),
            List.of(
                new HavingCondition(
                    AggregateFunction.SUM,
                    "quantity",
                    Operators.GREATER_THAN_OR_EQUAL,
                    (long) Math.max(query.getMinUnits(), 1))));
    return lines.findAllGrouped(plan).stream()
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
  @Transactional(readOnly = true)
  public List<RejectionCount> rejections(RejectionsQuery query) {
    return orders
        .findAllGrouped(
            QueryPlans.grouping(
                QueryPlans.requiring(query.getPlan(), REJECTED, PLACED_ORDERS),
                List.of("rejectionReason"),
                List.of(
                    new FieldSelection("rejectionReason"),
                    new AggregateSelection(AggregateFunction.COUNT, "orderId", "orders"))))
        .stream()
        .map(r -> new RejectionCount((String) r.get("rejectionReason"), count(r, "orders")))
        .sorted(Comparator.comparingLong(RejectionCount::orders).reversed())
        .toList();
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public List<CustomerSales> customers(CustomersQuery query) {
    return orders
        .findAllGrouped(
            QueryPlans.grouping(
                QueryPlans.requiring(query.getPlan(), CONFIRMED, PLACED_ORDERS),
                List.of("customerId", "currency"),
                List.of(
                    new FieldSelection("customerId"),
                    new FieldSelection("currency"),
                    new AggregateSelection(AggregateFunction.COUNT, "orderId", "orders"),
                    new AggregateSelection(AggregateFunction.SUM, "total", "spent"))))
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

  private static long count(GroupedRow row, String alias) {
    Object value = row.get(alias);
    return value == null ? 0 : ((Number) value).longValue();
  }

  private static BigDecimal money(GroupedRow row, String alias) {
    Object value = row.get(alias);
    return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
  }
}
