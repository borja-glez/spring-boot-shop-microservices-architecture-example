package com.borjaglez.shop.catalog.infrastructure.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.rabbitmq.RabbitMqQueryBus;

/**
 * Queries to other services over RabbitMQ. The query bus waits {@code
 * cqrs.rabbitmq.queries.reply-timeout} for the answer: a shopper's page waits a moment, then shows
 * what it has.
 */
@Configuration(proxyBeanMethods = false)
class RemoteQueriesConfiguration {

  @Bean
  RabbitStockLevelsGateway stockLevelsGateway(RabbitMqQueryBus remoteQueries) {
    return new RabbitStockLevelsGateway(remoteQueries);
  }
}
