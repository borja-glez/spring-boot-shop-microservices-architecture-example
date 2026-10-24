package com.borjaglez.shop.fixture.good.orders.application.port.out;

public record PaymentResult(Outcome outcome) {

  public enum Outcome {
    ACCEPTED,
    DECLINED
  }
}
