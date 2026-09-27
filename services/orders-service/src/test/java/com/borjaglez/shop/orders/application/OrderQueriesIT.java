package com.borjaglez.shop.orders.application;

import static com.borjaglez.shop.orders.OrdersTestSupport.published;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.contracts.orders.OrderCancelled;
import com.borjaglez.shop.contracts.orders.OrderConfirmed;
import com.borjaglez.shop.contracts.orders.OrderPlaced;
import com.borjaglez.shop.contracts.orders.OrderRejected;
import com.borjaglez.shop.eskit.EventStore;
import com.borjaglez.shop.eskit.RecordedEvent;
import com.borjaglez.shop.eskit.StoredEvent;
import com.borjaglez.shop.orders.application.command.CancelOrderCommand;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand.Item;
import com.borjaglez.shop.orders.application.projection.CatalogProductProjector;
import com.borjaglez.shop.orders.application.projection.OrderViewProjector;
import com.borjaglez.shop.orders.application.query.GetOrderHistoryQuery;
import com.borjaglez.shop.orders.application.query.GetOrderQuery;
import com.borjaglez.shop.orders.application.query.ListMyOrdersQuery;
import com.borjaglez.shop.orders.application.query.OrderViews.HistoryEntry;
import com.borjaglez.shop.orders.application.query.OrderViews.OrderDetail;
import com.borjaglez.shop.orders.application.query.OrderViews.OrderSummary;
import com.borjaglez.shop.orders.application.query.OrderViews.StoredEventView;
import com.borjaglez.shop.orders.application.query.SearchEventStoreQuery;
import com.borjaglez.shop.orders.checkout.CheckoutDriver;
import com.borjaglez.shop.orders.checkout.FakeCheckout;
import com.borjaglez.shop.orders.domain.Order;
import com.borjaglez.shop.orders.domain.OrderStatus;
import com.borjaglez.shop.orders.domain.OrderView;
import com.borjaglez.shop.support.error.NotFoundException;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;
import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.QueryPlanBuilder;
import com.borjaglez.specrepository.core.SpecificationQueryBuilder;

/**
 * Read side over a real database. The relay is off: the tests feed the read model from the event
 * store themselves, so what they see does not depend on Kafka timing.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "shop.outbox.relay.enabled=false",
      "shop.checkout.enabled=false",
      "shop.checkout.initial-backoff=0s",
      "shop.checkout.max-backoff=0s"
    })
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  FakeCheckout.class,
  CheckoutDriver.class
})
class OrderQueriesIT {

  private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20);
  private static final AllowedFieldsPolicy CLIENT_FIELDS =
      AllowedFieldsPolicy.of(Set.of("status", "total"), Set.of("placedAt", "total"));

  @Autowired CommandBus commands;
  @Autowired QueryBus queries;
  @Autowired EventStore eventStore;
  @Autowired CatalogProductProjector catalog;
  @Autowired OrderViewProjector projector;
  @Autowired CheckoutDriver checkout;

  private static String customer() {
    return "cliente-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private UUID product(String sku) {
    UUID id = UUID.randomUUID();
    catalog.on(published(id, sku, "4.00"));
    return id;
  }

  private UUID place(String customer, UUID... products) {
    UUID orderId =
        commands.dispatchAndReceive(
            new PlaceOrderCommand(
                customer, Arrays.stream(products).map(p -> new Item(p, 1)).toList()));
    project(orderId);
    return orderId;
  }

  /** Confirms the order through its checkout, then cancels it. */
  private void cancel(String customer, UUID orderId) {
    checkout.finish(orderId);
    commands.dispatchAndWait(new CancelOrderCommand(orderId, customer, "Ya no lo necesito"));
    project(orderId);
  }

  /** What the Kafka consumer would do; the projector ignores events it has already applied. */
  private void project(UUID orderId) {
    for (RecordedEvent recorded : eventStore.load(Order.STREAM_TYPE, orderId.toString())) {
      switch (recorded.event()) {
        case OrderPlaced placed -> projector.on(placed);
        case OrderConfirmed confirmed -> projector.on(confirmed);
        case OrderRejected rejected -> projector.on(rejected);
        case OrderCancelled cancelled -> projector.on(cancelled);
        default -> throw new IllegalStateException("Unexpected " + recorded.eventType());
      }
    }
  }

  private Page<OrderSummary> myOrders(String customer, QueryPlan<OrderView> plan) {
    return queries.ask(new ListMyOrdersQuery(customer, plan, FIRST_PAGE));
  }

  private static QueryPlanBuilder<OrderView> orders() {
    return SpecificationQueryBuilder.forEntity(OrderView.class);
  }

  @Test
  void customersOnlySeeTheirOwnOrders() {
    String lucia = customer();
    String mateo = customer();
    UUID coffee = product("CAF-" + lucia.substring(8, 12));
    UUID first = place(lucia, coffee);
    UUID second = place(lucia, coffee);
    place(mateo, coffee);

    assertThat(myOrders(lucia, orders().build()))
        .extracting(OrderSummary::orderId)
        .containsExactlyInAnyOrder(first, second);
  }

  @Test
  void theCustomerScopeHoldsUnderTheEndpointWhitelistAndAlternatives() {
    String lucia = customer();
    String mateo = customer();
    UUID coffee = product("CAF-" + lucia.substring(8, 12));
    UUID own = place(lucia, coffee);
    place(mateo, coffee);
    // customerId is not filterable by the client, yet the server scopes the rows by it; the
    // client's alternatives stay inside that scope.
    QueryPlan<OrderView> anyStatus =
        orders()
            .or(
                either ->
                    either
                        .where("status", Operators.IS_NOT_NULL, null)
                        .where("status", Operators.IS_NULL, null))
            .allowedFields(CLIENT_FIELDS)
            .build();

    assertThat(myOrders(lucia, anyStatus)).extracting(OrderSummary::orderId).containsExactly(own);
  }

  @Test
  void aClientFilterOutsideTheWhitelistIsRejected() {
    String lucia = customer();
    QueryPlan<OrderView> otherCustomer =
        orders()
            .where("customerId", Operators.EQUALS, "someone-else")
            .allowedFields(CLIENT_FIELDS)
            .build();

    assertThat(
            NestedExceptionUtils.getMostSpecificCause(
                catchThrowable(() -> myOrders(lucia, otherCustomer))))
        .isInstanceOf(DisallowedFieldException.class);
  }

  @Test
  void theClientPlanFiltersByStatus() {
    String lucia = customer();
    UUID coffee = product("CAF-" + lucia.substring(8, 12));
    UUID kept = place(lucia, coffee);
    UUID cancelled = place(lucia, coffee);
    cancel(lucia, cancelled);

    Page<OrderSummary> page =
        myOrders(lucia, orders().where("status", Operators.EQUALS, OrderStatus.CANCELLED).build());

    assertThat(page).extracting(OrderSummary::orderId).containsExactly(cancelled);
    assertThat(myOrders(lucia, orders().build())).extracting(OrderSummary::orderId).contains(kept);
  }

  @Test
  void ordersMatchingSeveralLinesOfTheFilterAppearOnce() {
    String lucia = customer();
    String prefix = "Q" + lucia.substring(8, 12);
    UUID order =
        place(lucia, product(prefix + "-A"), product(prefix + "-B"), product(prefix + "-C"));

    Page<OrderSummary> page =
        myOrders(lucia, orders().where("lines.sku", Operators.STARTS_WITH, prefix).build());

    assertThat(page.getContent()).extracting(OrderSummary::orderId).containsExactly(order);
    assertThat(page.getTotalElements()).isEqualTo(1);
  }

  @Test
  void theDetailIncludesTheLinesAndIsPrivate() {
    String lucia = customer();
    UUID coffee = product("CAF-" + lucia.substring(8, 12));
    UUID orderId = place(lucia, coffee);

    OrderDetail detail = queries.ask(new GetOrderQuery(orderId, lucia));

    assertThat(detail.status()).isEqualTo(OrderStatus.PLACED);
    assertThat(detail.lines())
        .singleElement()
        .satisfies(line -> assertThat(line.subtotal()).isEqualByComparingTo("4.00"));
    assertThatThrownBy(() -> queries.ask(new GetOrderQuery(orderId, customer())))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void theHistoryShowsTheStateAfterEachEvent() {
    String lucia = customer();
    UUID orderId = place(lucia, product("CAF-" + lucia.substring(8, 12)));
    cancel(lucia, orderId);

    List<HistoryEntry> history = queries.ask(new GetOrderHistoryQuery(orderId, lucia));

    assertThat(history).extracting(HistoryEntry::version).containsExactly(1L, 2L, 3L);
    assertThat(history)
        .extracting(HistoryEntry::eventType)
        .containsExactly(
            "shop.orders.1.event.order.order-placed",
            "shop.orders.1.event.order.order-confirmed",
            "shop.orders.1.event.order.order-cancelled");
    assertThat(history)
        .extracting(entry -> entry.stateAfter().status())
        .containsExactly(OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
    assertThat(history.getFirst().event()).isInstanceOf(OrderPlaced.class);
    assertThat(history.getFirst().publishedAt()).isNull();
  }

  @Test
  void otherCustomersCannotReadTheHistory() {
    UUID orderId =
        place(customer(), product("CAF-" + UUID.randomUUID().toString().substring(0, 4)));

    assertThatThrownBy(() -> queries.ask(new GetOrderHistoryQuery(orderId, customer())))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> queries.ask(new GetOrderHistoryQuery(UUID.randomUUID(), "nadie")))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void theExplorerFiltersTheEventStoreNewestFirst() {
    String lucia = customer();
    UUID orderId = place(lucia, product("CAF-" + lucia.substring(8, 12)));
    cancel(lucia, orderId);

    Page<StoredEventView> page =
        queries.ask(
            new SearchEventStoreQuery(
                SpecificationQueryBuilder.forEntity(StoredEvent.class)
                    .where("streamId", Operators.EQUALS, orderId.toString())
                    .build(),
                FIRST_PAGE));

    assertThat(page.getContent()).extracting(StoredEventView::version).containsExactly(3L, 2L, 1L);
    assertThat(page.getContent().getLast().payload()).contains(orderId.toString());
    assertThat(page.getContent().getLast().publishedAt()).isNull();
  }
}
