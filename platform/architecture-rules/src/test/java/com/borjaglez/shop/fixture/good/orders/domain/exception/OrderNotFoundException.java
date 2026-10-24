package com.borjaglez.shop.fixture.good.orders.domain.exception;

import com.borjaglez.shop.kernel.error.NotFoundException;

public class OrderNotFoundException extends NotFoundException {

  public OrderNotFoundException(String id) {
    super("order-not-found", "Order " + id + " not found");
  }
}
