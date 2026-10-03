package com.borjaglez.shop.orders.application.query;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.eskit.EventStore;
import com.borjaglez.shop.eskit.RecordedEvent;
import com.borjaglez.shop.eskit.StoredEventRepository;
import com.borjaglez.shop.orders.application.query.OrderViews.CheckoutView;
import com.borjaglez.shop.orders.application.query.OrderViews.HistoryEntry;
import com.borjaglez.shop.orders.application.query.OrderViews.OrderDetail;
import com.borjaglez.shop.orders.application.query.OrderViews.OrderSummary;
import com.borjaglez.shop.orders.application.query.OrderViews.StoredEventView;
import com.borjaglez.shop.orders.domain.CheckoutSagaRepository;
import com.borjaglez.shop.orders.domain.Order;
import com.borjaglez.shop.orders.domain.OrderViewRepository;
import com.borjaglez.shop.support.error.NotFoundException;
import com.borjaglez.specrepository.core.Operators;

/**
 * Read side of orders: the read model for lists and details, the event store for history and
 * exploration. Every read uses specification-repository.
 */
@QueryHandler
public class OrderQueryHandler {

  /**
   * Newest first when the customer picks no order. {@code orderId} is not a sortable field of "my
   * orders", but this is a server sort: it is not checked against the whitelist, and it breaks the
   * ties between orders placed at the same instant so pages never overlap.
   */
  static final Sort NEWEST_ORDERS = Sort.by(Sort.Order.desc("placedAt"), Sort.Order.asc("orderId"));

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

  /**
   * The lists take the client's filters: a combination the indexes do not cover must not hold a
   * connection for long. Spring applies the transaction timeout to every JPA query run in it.
   */
  static final int LIST_TIMEOUT_SECONDS = 5;

  @HandleQuery
  @Transactional(readOnly = true, timeout = LIST_TIMEOUT_SECONDS)
  public Page<OrderSummary> myOrders(ListMyOrdersQuery query) {
    // customerId is a server condition: the client cannot filter by it, and its orFilter
    // alternatives stay within the current customer's orders.
    return views
        .query(query.getPlan())
        .where("customerId", Operators.EQUALS, query.getCustomerId())
        .sortedByDefault(NEWEST_ORDERS)
        .findAll(query.getPageable())
        .map(OrderViews::summary);
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

  /**
   * The event store only grows, so the explorer reads a {@code Slice}: one row more than the page
   * to know whether there is a next one, and no {@code COUNT(*)} over the whole table.
   */
  @HandleQuery
  @Transactional(readOnly = true, timeout = LIST_TIMEOUT_SECONDS)
  public Slice<StoredEventView> events(SearchEventStoreQuery query) {
    return storedEvents
        .query(query.getPlan())
        .sortedByDefault(NEWEST_EVENTS)
        .findSlice(query.getPageable())
        .map(OrderViews::stored);
  }

  private static NotFoundException notFound(Object orderId) {
    return new NotFoundException("order-not-found", "Unknown order " + orderId);
  }
}
