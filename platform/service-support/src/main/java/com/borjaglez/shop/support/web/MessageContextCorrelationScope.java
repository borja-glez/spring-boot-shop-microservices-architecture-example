package com.borjaglez.shop.support.web;

import com.borjaglez.cqrs.context.MessageContext;

/**
 * Copies the request correlation id into the spring-boot-cqrs {@link MessageContext}, so the
 * library's context propagation middleware attaches it to every message dispatched while serving
 * the request.
 */
public class MessageContextCorrelationScope implements CorrelationScope {

  @Override
  public AutoCloseable open(String correlationId) {
    MessageContext context =
        MessageContext.current().with(MessageContext.CORRELATION_ID_KEY, correlationId);
    return MessageContext.scope(context);
  }
}
