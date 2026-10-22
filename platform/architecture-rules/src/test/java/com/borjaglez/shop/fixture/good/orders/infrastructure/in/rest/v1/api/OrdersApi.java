package com.borjaglez.shop.fixture.good.orders.infrastructure.in.rest.v1.api;

import com.borjaglez.shop.fixture.good.orders.infrastructure.in.rest.v1.model.OrderModel;

public interface OrdersApi {

  OrderModel place(String id);
}
