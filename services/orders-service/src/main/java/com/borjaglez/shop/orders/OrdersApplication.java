package com.borjaglez.shop.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrdersApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrdersApplication.class, args);
  }
}
