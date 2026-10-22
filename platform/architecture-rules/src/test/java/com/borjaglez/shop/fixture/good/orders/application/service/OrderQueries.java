package com.borjaglez.shop.fixture.good.orders.application.service;

import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.contracts.FixtureMessage;
import com.borjaglez.shop.fixture.good.orders.domain.OrderRepository;

@QueryHandler
public class OrderQueries {

  private final OrderRepository orders;

  public OrderQueries(OrderRepository orders) {
    this.orders = orders;
  }

  public FixtureMessage find(String id) {
    return orders.findById(id).map(order -> new FixtureMessage(order.id())).orElse(null);
  }
}
