package com.borjaglez.shop.fixture.bad.orders.application.exception;

import com.borjaglez.shop.kernel.error.NotFoundException;

public class OrderNotFound extends NotFoundException {

  public OrderNotFound(String id) {
    super("order-not-found", "Order " + id + " not found");
  }
}
