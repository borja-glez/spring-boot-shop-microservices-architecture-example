package com.borjaglez.shop.orders.application.query;

import java.util.List;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.orders.application.query.OrderViews.OrderNoticesView;
import com.borjaglez.shop.orders.domain.OrderViewRepository;
import com.borjaglez.shop.support.error.NotFoundException;
import com.borjaglez.specrepository.core.Operators;

/**
 * The notices of an order, which only the notifications service knows: it is asked over RabbitMQ.
 * The order must belong to the customer, checked here before asking; the notifications service
 * filters by customer as well.
 */
@QueryHandler
public class OrderNoticesHandler {

  private final OrderViewRepository views;
  private final OrderNoticesGateway notices;

  public OrderNoticesHandler(OrderViewRepository views, OrderNoticesGateway notices) {
    this.views = views;
    this.notices = notices;
  }

  @HandleQuery
  public OrderNoticesView notices(GetOrderNoticesQuery query) {
    boolean owned =
        views
                .query()
                .where("orderId", Operators.EQUALS, query.getOrderId())
                .where("customerId", Operators.EQUALS, query.getCustomerId())
                .count()
            > 0;
    if (!owned) {
      throw new NotFoundException("order-not-found", "Unknown order " + query.getOrderId());
    }
    return notices
        .notices(query.getOrderId(), query.getCustomerId())
        .map(list -> new OrderNoticesView(true, list))
        .orElseGet(() -> new OrderNoticesView(false, List.of()));
  }
}
