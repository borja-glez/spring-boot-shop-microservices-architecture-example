package com.borjaglez.shop.fixture.bad.orders.application.service;

import org.springframework.beans.factory.annotation.Value;

public class ValueField {

  @Value("${setting}")
  private String setting;
}
