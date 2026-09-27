package com.borjaglez.shop.reporting.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.query.QueryBus;
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
import com.borjaglez.shop.reporting.application.rebuild.RebuildService;
import com.borjaglez.shop.reporting.application.rebuild.RebuildService.RebuildStatus;
import com.borjaglez.shop.reporting.domain.ReportLine;
import com.borjaglez.shop.reporting.domain.ReportOrder;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.http.spring.FilterableQuery;

/**
 * Backoffice reports. Order reports accept HTTP filters on the placement date and the currency
 * ({@link OrderReportFilter}); the product report on the order's date and on the product. What is
 * grouped and summed is the server's choice, never a parameter.
 */
@RestController
@RequestMapping("/api/reporting")
class ReportingController {

  private final QueryBus queries;
  private final RebuildService rebuilds;

  ReportingController(QueryBus queries, RebuildService rebuilds) {
    this.queries = queries;
    this.rebuilds = rebuilds;
  }

  @GetMapping("/summary")
  List<StatusCount> summary(@OrderReportFilter QueryPlan<ReportOrder> plan) {
    return queries.ask(new SummaryQuery(plan));
  }

  @GetMapping("/sales-by-day")
  List<DailySales> salesByDay(@OrderReportFilter QueryPlan<ReportOrder> plan) {
    return queries.ask(new SalesByDayQuery(plan));
  }

  @GetMapping("/top-products")
  List<ProductSales> topProducts(
      @FilterableQuery(
              value = ReportLine.class,
              filterableFields = {"order.placedAt", "order.placedDay", "sku", "name"})
          QueryPlan<ReportLine> plan,
      @RequestParam(defaultValue = "1") int minUnits) {
    return queries.ask(new TopProductsQuery(plan, minUnits));
  }

  @GetMapping("/rejections")
  List<RejectionCount> rejections(@OrderReportFilter QueryPlan<ReportOrder> plan) {
    return queries.ask(new RejectionsQuery(plan));
  }

  @GetMapping("/customers")
  List<CustomerSales> customers(@OrderReportFilter QueryPlan<ReportOrder> plan) {
    return queries.ask(new CustomersQuery(plan));
  }

  /** Clears the projections and reads every event again. Answers when the replay has started. */
  @PostMapping("/rebuild")
  RebuildStatus rebuild() {
    return rebuilds.rebuild();
  }

  @GetMapping("/rebuild")
  RebuildStatus rebuildStatus() {
    return rebuilds.status();
  }
}
