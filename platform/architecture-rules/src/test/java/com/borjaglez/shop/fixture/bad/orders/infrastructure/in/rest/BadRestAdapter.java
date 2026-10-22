package com.borjaglez.shop.fixture.bad.orders.infrastructure.in.rest;

import com.borjaglez.shop.fixture.bad.orders.infrastructure.in.messaging.MessagingAdapter;
import com.borjaglez.shop.fixture.bad.orders.infrastructure.out.persistence.BadEntity;

public class BadRestAdapter {

  private BadEntity entity;
  private MessagingAdapter messaging;
}
