package com.borjaglez.shop.catalog.infrastructure.messaging;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.RabbitMqQueryBus;
import com.borjaglez.cqrs.rabbitmq.config.RabbitMqCqrsProperties;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

/**
 * Queries to other services over RabbitMQ. They get their own template and reply timeout ({@code
 * shop.remote-queries.reply-timeout}): a shopper's page waits a moment for the answer, then shows
 * what it has. The rest of the template comes from {@code spring.rabbitmq.template}.
 */
@Configuration(proxyBeanMethods = false)
class RemoteQueriesConfiguration {

  @Bean
  RabbitStockLevelsGateway stockLevelsGateway(
      RabbitTemplateConfigurer configurer,
      ConnectionFactory connectionFactory,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      RabbitMqCqrsProperties properties,
      ObjectProvider<List<BusMiddleware>> middlewares,
      @Value("${cqrs.context.header-prefix:cqrs.context.}") String contextHeaderPrefix,
      @Value("${shop.remote-queries.reply-timeout:1s}") Duration replyTimeout) {
    RabbitTemplate template = new RabbitTemplate();
    configurer.configure(template, connectionFactory);
    template.setReplyTimeout(replyTimeout.toMillis());
    RabbitMqQueryBus remoteQueries =
        new RabbitMqQueryBus(
            new RabbitMqPublisher(template, contextHeaderPrefix),
            rabbitNaming,
            messageNaming,
            properties.getQueries().getExchange(),
            middlewares.getIfAvailable(Collections::emptyList));
    return new RabbitStockLevelsGateway(remoteQueries);
  }
}
