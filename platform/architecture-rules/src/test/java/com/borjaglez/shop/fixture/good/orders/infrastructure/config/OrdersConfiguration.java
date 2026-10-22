package com.borjaglez.shop.fixture.good.orders.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.shop.fixture.good.orders.application.service.PlaceOrderHandler;
import com.borjaglez.shop.fixture.good.orders.infrastructure.in.rest.OrderController;
import com.borjaglez.shop.fixture.good.orders.infrastructure.out.messaging.OrderShipments;
import com.borjaglez.shop.fixture.good.orders.infrastructure.out.payments.FakePayments;
import com.borjaglez.shop.fixture.good.orders.infrastructure.out.persistence.JpaOrderRepository;

@Configuration
public class OrdersConfiguration {

  @Bean
  PlaceOrderHandler placeOrderHandler() {
    return new PlaceOrderHandler(new JpaOrderRepository(), new FakePayments());
  }

  @Bean
  OrderController orderController(PlaceOrderHandler handler) {
    return new OrderController(handler);
  }

  @Bean
  OrderShipments orderShipments() {
    return new OrderShipments();
  }
}
