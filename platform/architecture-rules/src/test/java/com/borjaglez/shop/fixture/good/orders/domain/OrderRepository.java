package com.borjaglez.shop.fixture.good.orders.domain;

import java.util.Optional;

public interface OrderRepository {

  void save(Order order);

  Optional<Order> findById(String id);
}
