package com.borjaglez.shop.fixture.bad.orders.domain;

import java.time.Instant;

import com.borjaglez.shop.kernel.domain.DomainEvent;

public final class ClassEvent implements DomainEvent {

  @Override
  public Instant occurredOn() {
    return Instant.EPOCH;
  }
}
