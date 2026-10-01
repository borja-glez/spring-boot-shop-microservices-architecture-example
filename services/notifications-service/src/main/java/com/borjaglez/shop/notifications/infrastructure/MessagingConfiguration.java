package com.borjaglez.shop.notifications.infrastructure;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.rabbitmq.config.RabbitMqCqrsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON over RabbitMQ with Spring Boot's {@code ObjectMapper}, which writes dates as ISO-8601 text,
 * as Jackson 3 does in the Boot 4 services. The converter Spring AMQP builds on its own would write
 * them as numbers, which the other side has to guess the meaning of.
 */
@Configuration(proxyBeanMethods = false)
class MessagingConfiguration {

  @Bean
  MessageConverter cqrsMessageConverter(
      ObjectMapper objectMapper, RabbitMqCqrsProperties properties) {
    return new Jackson2JsonMessageConverter(
        objectMapper, properties.getTrustedPackages().toArray(String[]::new));
  }
}
