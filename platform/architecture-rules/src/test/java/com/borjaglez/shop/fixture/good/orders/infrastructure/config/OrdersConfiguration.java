package com.borjaglez.shop.fixture.good.orders.infrastructure.config;

import java.time.Clock;
import java.time.InstantSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.shop.fixture.good.orders.application.port.out.PaymentPort;
import com.borjaglez.shop.fixture.good.orders.application.service.PlaceOrderHandler;
import com.borjaglez.shop.fixture.good.orders.domain.OrderRepository;
import com.borjaglez.shop.fixture.good.orders.infrastructure.in.rest.OrderController;
import com.borjaglez.shop.fixture.good.orders.infrastructure.out.messaging.OrderShipments;
import com.borjaglez.shop.fixture.good.orders.infrastructure.out.payments.FakePayments;
import com.borjaglez.shop.fixture.good.orders.infrastructure.out.persistence.JpaOrderRepository;

@Configuration
public class OrdersConfiguration {

  /** An interface outside the service may be the declared type. */
  @Bean
  InstantSource clock() {
    return Clock.systemUTC();
  }

  @Bean
  JpaOrderRepository orderRepository() {
    return new JpaOrderRepository();
  }

  @Bean
  FakePayments payments() {
    return new FakePayments();
  }

  @Bean
  PlaceOrderHandler placeOrderHandler(OrderRepository orders, PaymentPort payments) {
    return new PlaceOrderHandler(orders, payments);
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
