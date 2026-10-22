package com.borjaglez.shop.fixture.good.orders.application.service;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.shop.fixture.good.orders.application.port.out.PaymentPort;
import com.borjaglez.shop.fixture.good.orders.application.port.out.PaymentResult;
import com.borjaglez.shop.fixture.good.orders.domain.Order;
import com.borjaglez.shop.fixture.good.orders.domain.OrderRepository;

@CommandHandler
public class PlaceOrderHandler {

  private final OrderRepository orders;
  private final PaymentPort payments;

  public PlaceOrderHandler(OrderRepository orders, PaymentPort payments) {
    this.orders = orders;
    this.payments = payments;
  }

  public boolean handle(String id) {
    Order order = new Order(id);
    order.place();
    orders.save(order);
    return payments.charge(id).outcome() == PaymentResult.Outcome.ACCEPTED;
  }
}
