package com.borjaglez.shop.fixture.good.orders.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.shop.fixture.good.orders.application.service.PlaceOrderHandler;
import com.borjaglez.shop.fixture.good.orders.infrastructure.out.payments.FakePayments;
import com.borjaglez.shop.fixture.good.orders.infrastructure.out.persistence.JpaOrderRepository;

@Configuration
public class OrdersConfiguration {

  @Bean
  PlaceOrderHandler placeOrderHandler() {
    return new PlaceOrderHandler(new JpaOrderRepository(), new FakePayments());
  }
}
