package com.borjaglez.shop.fixture.good.orders.infrastructure.in.rest;

import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.shop.fixture.good.orders.application.service.PlaceOrderHandler;
import com.borjaglez.shop.fixture.good.orders.infrastructure.in.rest.v1.api.OrdersApi;
import com.borjaglez.shop.fixture.good.orders.infrastructure.in.rest.v1.model.OrderModel;

@RestController
public class OrderController implements OrdersApi {

  private final PlaceOrderHandler handler;

  public OrderController(PlaceOrderHandler handler) {
    this.handler = handler;
  }

  @Override
  public OrderModel place(String id) {
    return new OrderModel(id, handler.handle(id));
  }
}
