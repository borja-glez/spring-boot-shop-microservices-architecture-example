package com.borjaglez.shop.fixture.good.orders.infrastructure.out.persistence;

import java.util.Optional;

import com.borjaglez.shop.fixture.good.orders.domain.Order;
import com.borjaglez.shop.fixture.good.orders.domain.OrderRepository;

public class JpaOrderRepository implements OrderRepository {

  @Override
  public void save(Order order) {}

  @Override
  public Optional<Order> findById(String id) {
    return Optional.of(new Order(id));
  }
}
