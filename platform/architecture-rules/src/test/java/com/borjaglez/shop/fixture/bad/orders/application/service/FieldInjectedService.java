package com.borjaglez.shop.fixture.bad.orders.application.service;

import org.springframework.beans.factory.annotation.Autowired;

public class FieldInjectedService {

  @Autowired private Object dependency;
}
