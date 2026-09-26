package com.borjaglez.shop.orders.application.query;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.eskit.EventStore;
import com.borjaglez.shop.eskit.RecordedEvent;
import com.borjaglez.shop.eskit.StoredEvent;
import com.borjaglez.shop.eskit.StoredEventRepository;
import com.borjaglez.shop.orders.application.query.OrderViews.CheckoutView;
import com.borjaglez.shop.orders.application.query.OrderViews.HistoryEntry;
import com.borjaglez.shop.orders.application.query.OrderViews.OrderDetail;
import com.borjaglez.shop.orders.application.query.OrderViews.OrderSummary;
import com.borjaglez.shop.orders.application.query.OrderViews.StoredEventView;
import com.borjaglez.shop.orders.domain.CheckoutSagaRepository;
import com.borjaglez.shop.orders.domain.Order;
import com.borjaglez.shop.orders.domain.OrderView;
import com.borjaglez.shop.orders.domain.OrderViewRepository;
import com.borjaglez.shop.support.error.NotFoundException;
import com.borjaglez.shop.support.query.QueryPlans;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryPlan;

/**
 * Read side of orders: the read model for lists and details, the event store for history and
 * exploration. Every read uses specification-repository.
 */
@QueryHandler
public class OrderQueryHandler {

  private static final Sort NEWEST_ORDERS = Sort.by(Sort.Direction.DESC, "placedAt");
  private static final Sort NEWEST_EVENTS = Sort.by(Sort.Direction.DESC, "globalPosition");

  private final OrderViewRepository views;
  private final EventStore eventStore;
  private final StoredEventRepository storedEvents;
  private final CheckoutSagaRepository sagas;

  public OrderQueryHandler(
      OrderViewRepository views,
      EventStore eventStore,
      StoredEventRepository storedEvents,
      CheckoutSagaRepository sagas) {
    this.views = views;
    this.eventStore = eventStore;
    this.storedEvents = storedEvents;
    this.sagas = sagas;
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public Page<OrderSummary> myOrders(ListMyOrdersQuery query) {
    QueryPlan<OrderView> plan =
        QueryPlans.sortedByDefault(
            QueryPlans.requiring(
                query.getPlan(),
                new PredicateCondition(
                    "customerId", Operators.EQUALS, query.getCustomerId(), false, false)),
            NEWEST_ORDERS);
    return views.findAll(plan, query.getPageable()).map(OrderViews::summary);
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public OrderDetail order(GetOrderQuery query) {
    return views
        .query()
        .where("orderId", Operators.EQUALS, query.getOrderId())
        .where("customerId", Operators.EQUALS, query.getCustomerId())
        .findOne()
        .map(OrderViews::detail)
        .orElseThrow(() -> notFound(query.getOrderId()));
  }

  /**
   * Replays the stream one event at a time to show the state after each version. The ownership
   * check needs the whole stream, since the customer is only known from the first event.
   */
  @HandleQuery
  @Transactional(readOnly = true)
  public List<HistoryEntry> history(GetOrderHistoryQuery query) {
    List<RecordedEvent> stream = eventStore.load(Order.STREAM_TYPE, query.getOrderId().toString());
    Order order = new Order();
    List<HistoryEntry> entries = new ArrayList<>(stream.size());
    for (RecordedEvent recorded : stream) {
      order.replay(List.of(recorded.event()));
      entries.add(
          new HistoryEntry(
              recorded.version(),
              recorded.eventType(),
              recorded.occurredAt(),
              recorded.publishedAt(),
              recorded.event(),
              OrderViews.state(order)));
    }
    if (entries.isEmpty() || !order.belongsTo(query.getCustomerId())) {
      throw notFound(query.getOrderId());
    }
    return entries;
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public CheckoutView checkout(GetCheckoutQuery query) {
    return sagas
        .query()
        .where("orderId", Operators.EQUALS, query.getOrderId())
        .where("customerId", Operators.EQUALS, query.getCustomerId())
        .findOne()
        .map(OrderViews::checkout)
        .orElseThrow(() -> notFound(query.getOrderId()));
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public Page<StoredEventView> events(SearchEventStoreQuery query) {
    QueryPlan<StoredEvent> plan = QueryPlans.sortedByDefault(query.getPlan(), NEWEST_EVENTS);
    return storedEvents.findAll(plan, query.getPageable()).map(OrderViews::stored);
  }

  private static NotFoundException notFound(Object orderId) {
    return new NotFoundException("order-not-found", "Unknown order " + orderId);
  }
}
