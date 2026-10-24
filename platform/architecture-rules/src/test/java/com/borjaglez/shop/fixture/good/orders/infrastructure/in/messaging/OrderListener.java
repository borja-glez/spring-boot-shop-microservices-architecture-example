package com.borjaglez.shop.fixture.good.orders.infrastructure.in.messaging;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.shop.fixture.good.orders.application.service.PlaceOrderHandler;

@EventHandler
public class OrderListener {

  private final PlaceOrderHandler handler;

  public OrderListener(PlaceOrderHandler handler) {
    this.handler = handler;
  }

  public void on(String id) {
    handler.handle(id);
  }
}
