package com.borjaglez.shop.fixture.good.orders.application.port.out;

public interface PaymentPort {

  PaymentResult charge(String orderId);
}
