package com.borjaglez.shop.fixture.bad.orders.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.shop.fixture.bad.orders.application.port.out.StockPort;
import com.borjaglez.shop.fixture.bad.orders.domain.ProductRepository;

@Configuration
public class PortTypedConfiguration {

  @Bean
  ProductRepository productRepository() {
    return name -> {};
  }

  @Bean
  StockPort stockPort() {
    return sku -> 0;
  }
}
