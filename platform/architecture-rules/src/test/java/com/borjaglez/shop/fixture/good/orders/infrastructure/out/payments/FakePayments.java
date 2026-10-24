package com.borjaglez.shop.fixture.good.orders.infrastructure.out.payments;

import com.borjaglez.shop.fixture.good.orders.application.port.out.PaymentPort;
import com.borjaglez.shop.fixture.good.orders.application.port.out.PaymentResult;

public class FakePayments implements PaymentPort {

  @Override
  public PaymentResult charge(String orderId) {
    return new PaymentResult(PaymentResult.Outcome.ACCEPTED);
  }
}
