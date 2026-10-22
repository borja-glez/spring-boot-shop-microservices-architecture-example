package com.borjaglez.shop.fixture.good.orders.infrastructure.in.rest;

import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.shop.fixture.good.orders.application.service.PlaceOrderHandler;
import com.borjaglez.shop.fixture.good.orders.infrastructure.in.rest.dto.OrderView;

@RestController
public class OrderController {

  private final PlaceOrderHandler handler;

  public OrderController(PlaceOrderHandler handler) {
    this.handler = handler;
  }

  public OrderView place(String id) {
    return new OrderView(id, handler.handle(id));
  }
}
