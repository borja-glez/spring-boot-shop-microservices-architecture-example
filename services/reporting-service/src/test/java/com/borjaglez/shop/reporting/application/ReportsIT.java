package com.borjaglez.shop.reporting.application;

import static com.borjaglez.shop.reporting.ReportingTestEvents.placed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;

import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.contracts.orders.OrderCancelled;
import com.borjaglez.shop.contracts.orders.OrderConfirmed;
import com.borjaglez.shop.contracts.orders.OrderRejected;
import com.borjaglez.shop.reporting.application.projection.ReportProjector;
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
import com.borjaglez.shop.reporting.domain.ReportLine;
import com.borjaglez.shop.reporting.domain.ReportOrder;
import com.borjaglez.shop.reporting.domain.ReportOrderRepository;
import com.borjaglez.shop.reporting.domain.ReportStatus;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.SpecificationQueryBuilder;

/**
 * Projections and reports over a real database. Each test uses its own day and products and filters
 * on them, the way the backoffice filters by dates.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({PostgresTestConfiguration.class, KafkaTestConfiguration.class})
class ReportsIT {

  private static final AllowedFieldsPolicy ORDER_FILTERS =
      AllowedFieldsPolicy.of(Set.of("placedAt", "placedDay", "currency"), Set.of());
  private static final AllowedFieldsPolicy LINE_FILTERS =
      AllowedFieldsPolicy.of(Set.of("order.placedAt", "order.placedDay", "sku", "name"), Set.of());

  @Autowired ReportProjector projector;
  @Autowired EventHandlerRegistry handlers;
  @Autowired QueryBus queries;
  @Autowired ReportOrderRepository orders;

  private static QueryPlan<ReportOrder> on(String day) {
    return SpecificationQueryBuilder.forEntity(ReportOrder.class)
        .where("placedDay", Operators.EQUALS, LocalDate.parse(day))
        .allowedFields(ORDER_FILTERS)
        .build();
  }

  private static QueryPlan<ReportLine> skus(String prefix) {
    return SpecificationQueryBuilder.forEntity(ReportLine.class)
        .where("sku", Operators.STARTS_WITH, prefix)
        .allowedFields(LINE_FILTERS)
        .build();
  }

  private UUID confirmed(String customer, String sku, int units, String price, String day) {
    UUID orderId = UUID.randomUUID();
    projector.on(placed(orderId, customer, sku, units, price, day));
    projector.on(new OrderConfirmed(orderId, UUID.randomUUID()));
    return orderId;
  }

  @Test
  void salesCountConfirmedOrdersOnly() {
    String day = "2026-01-01";
    confirmed("ana", "S1-A", 2, "10.00", day);
    confirmed("ana", "S1-B", 1, "5.50", day);
    confirmed("luis", "S1-A", 1, "10.00", day);
    UUID rejected = UUID.randomUUID();
    projector.on(placed(rejected, "luis", "S1-A", 9, "10.00", day));
    projector.on(new OrderRejected(rejected, "card-limit-exceeded", "x"));

    List<DailySales> sales = queries.ask(new SalesByDayQuery(on(day)));

    assertThat(sales)
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.day()).isEqualTo(LocalDate.parse(day));
              assertThat(s.orders()).isEqualTo(3);
              assertThat(s.customers()).isEqualTo(2);
              assertThat(s.revenue()).isEqualByComparingTo("35.50");
            });
    assertThat(queries.<List<StatusCount>>ask(new SummaryQuery(on(day))))
        .containsExactly(
            new StatusCount(ReportStatus.CONFIRMED, 3), new StatusCount(ReportStatus.REJECTED, 1));
    assertThat(queries.<List<RejectionCount>>ask(new RejectionsQuery(on(day))))
        .containsExactly(new RejectionCount("card-limit-exceeded", 1));
  }

  @Test
  void topProductsKeepThoseAboveTheMinimum() {
    String day = "2026-01-02";
    confirmed("ana", "T2-CAFE", 3, "4.00", day);
    confirmed("luis", "T2-CAFE", 2, "4.00", day);
    confirmed("ana", "T2-TE", 1, "3.00", day);
    UUID cancelled = confirmed("eva", "T2-TE", 5, "3.00", day);
    projector.on(new OrderCancelled(cancelled, "prisa", "eva"));

    List<ProductSales> top = queries.ask(new TopProductsQuery(skus("T2-"), 2));

    // T2-TE sold 1 unit in confirmed orders (the cancelled 5 do not count), below the minimum of 2.
    assertThat(top)
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.sku()).isEqualTo("T2-CAFE");
              assertThat(p.units()).isEqualTo(5);
              assertThat(p.orders()).isEqualTo(2);
              assertThat(p.revenue()).isEqualByComparingTo("20.00");
            });
  }

  @Test
  void clientFiltersOutsideTheWhitelistAreRejected() {
    QueryPlan<ReportOrder> byStatus =
        SpecificationQueryBuilder.forEntity(ReportOrder.class)
            .where("status", Operators.EQUALS, ReportStatus.CONFIRMED)
            .allowedFields(ORDER_FILTERS)
            .build();
    QueryPlan<ReportLine> byQuantity =
        SpecificationQueryBuilder.forEntity(ReportLine.class)
            .where("quantity", Operators.GREATER_THAN, 1)
            .allowedFields(LINE_FILTERS)
            .build();

    assertThat(
            NestedExceptionUtils.getMostSpecificCause(
                catchThrowable(() -> queries.ask(new SalesByDayQuery(byStatus)))))
        .isInstanceOf(DisallowedFieldException.class);
    assertThat(
            NestedExceptionUtils.getMostSpecificCause(
                catchThrowable(() -> queries.ask(new TopProductsQuery(byQuantity, 1)))))
        .isInstanceOf(DisallowedFieldException.class);
  }

  @Test
  void customersAreRankedBySpending() {
    String day = "2026-01-03";
    confirmed("c3-ana", "C3-A", 1, "30.00", day);
    confirmed("c3-luis", "C3-A", 1, "10.00", day);
    confirmed("c3-luis", "C3-A", 1, "15.00", day);

    List<CustomerSales> customers = queries.ask(new CustomersQuery(on(day)));

    assertThat(customers)
        .extracting(CustomerSales::customerId, CustomerSales::orders)
        .containsExactly(tuple("c3-ana", 1L), tuple("c3-luis", 2L));
  }

  @Test
  void eventsInAnyOrderAndTwiceGiveTheSameRow() {
    String day = "2026-01-04";
    UUID orderId = UUID.randomUUID();
    OrderConfirmed confirmation = new OrderConfirmed(orderId, UUID.randomUUID());
    var placement = placed(orderId, "ana", "O4-A", 2, "7.00", day);

    // Through the handler registry, as the Kafka consumer delivers them.
    handlers.handle(confirmation);
    handlers.handle(new OrderCancelled(orderId, "x", "ana"));
    handlers.handle(placement);
    handlers.handle(placement);
    handlers.handle(confirmation);

    ReportOrder order =
        orders.query().where("orderId", Operators.EQUALS, orderId).findOne().orElseThrow();
    assertThat(order.getStatus()).isEqualTo(ReportStatus.CANCELLED);
    assertThat(order.getTotal()).isEqualByComparingTo("14.00");
    assertThat(order.getPlacedDay()).isEqualTo(LocalDate.parse(day));
    assertThat(queries.<List<ProductSales>>ask(new TopProductsQuery(skus("O4-"), 1))).isEmpty();
  }
}
