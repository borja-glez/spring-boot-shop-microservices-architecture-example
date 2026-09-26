package com.borjaglez.shop.orders.infrastructure.checkout;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.rabbitmq.RabbitMqCommandBus;
import com.borjaglez.shop.orders.application.checkout.CheckoutProperties;
import com.borjaglez.shop.orders.application.checkout.CheckoutSagaRunner;

/** Wires the checkout saga to RabbitMQ and schedules its runner. */
@Configuration(proxyBeanMethods = false)
class CheckoutConfiguration {

  /** Inventory and payments, reached over RabbitMQ; the saga sees them as two ports. */
  @Bean
  RabbitCheckoutGateways checkoutGateways(RabbitMqCommandBus rabbitMqCommandBus) {
    return new RabbitCheckoutGateways(rabbitMqCommandBus);
  }

  @Bean
  @ConditionalOnBooleanProperty(name = "shop.checkout.enabled", matchIfMissing = true)
  CheckoutSagaScheduler checkoutSagaScheduler(
      CheckoutSagaRunner runner, CheckoutProperties properties) {
    return new CheckoutSagaScheduler(runner, properties.pollInterval());
  }
}
