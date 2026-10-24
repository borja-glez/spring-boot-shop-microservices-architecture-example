package com.borjaglez.shop.fixture.good.orders.application.service;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.shop.kernel.domain.DomainEvent;

@EventHandler
public class OrderReactions {

  public void on(DomainEvent event) {}
}
