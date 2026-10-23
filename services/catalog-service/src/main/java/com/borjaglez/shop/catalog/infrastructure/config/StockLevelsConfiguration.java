package com.borjaglez.shop.catalog.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.rabbitmq.RabbitMqQueryBus;
import com.borjaglez.shop.catalog.application.port.out.StockLevels;
import com.borjaglez.shop.catalog.infrastructure.out.messaging.RabbitStockLevels;

/**
 * [Hexagonal · composition root] Plugs the inventory, asked over RabbitMQ, into the {@link
 * StockLevels} port. The query bus waits {@code cqrs.rabbitmq.queries.reply-timeout} for the
 * answer: a shopper's page waits a moment, then shows what it has.
 */
@Configuration(proxyBeanMethods = false)
public class StockLevelsConfiguration {

  @Bean
  RabbitStockLevels stockLevels(RabbitMqQueryBus remoteQueries) {
    return new RabbitStockLevels(remoteQueries);
  }
}
