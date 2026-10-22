package com.borjaglez.shop.fixture.good.orders.infrastructure.out.persistence;

import jakarta.persistence.Entity;

@Entity
public class OrderEntity {

  private String id;

  public String getId() {
    return id;
  }
}
