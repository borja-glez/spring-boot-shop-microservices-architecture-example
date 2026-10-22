package com.borjaglez.shop.fixture.good.orders.domain;

import java.time.Instant;

import com.borjaglez.shop.fixture.good.orders.domain.event.OrderEvent;
import com.borjaglez.shop.fixture.good.orders.domain.event.OrderPlaced;
import com.borjaglez.shop.kernel.domain.AggregateRoot;

public class Order extends AggregateRoot<OrderEvent> {

  public static final String PREFIX = "order-";

  private final String id;
  private boolean placed;

  public Order(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }

  public void place() {
    placed = true;
    record(new OrderPlaced(id, Instant.EPOCH));
  }

  public boolean isPlaced() {
    return placed;
  }
}
