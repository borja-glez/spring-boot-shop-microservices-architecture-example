package com.borjaglez.shop.orders.infrastructure.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.rabbitmq.RabbitMqQueryBus;

/**
 * Queries to other services over RabbitMQ. The query bus waits {@code
 * cqrs.rabbitmq.queries.reply-timeout} for the answer, less than the checkout commands wait ({@code
 * cqrs.rabbitmq.commands.reply-timeout}): a shopper waits a moment, then sees what there is.
 */
@Configuration(proxyBeanMethods = false)
class RemoteQueriesConfiguration {

  @Bean
  RabbitRemoteQueries remoteQueries(RabbitMqQueryBus remoteQueries) {
    return new RabbitRemoteQueries(remoteQueries);
  }
}
