package com.borjaglez.shop.fixture.good.billing.domain;

public class Invoice {

  private final String number;

  public Invoice(String number) {
    this.number = number;
  }

  public String number() {
    return number;
  }
}
