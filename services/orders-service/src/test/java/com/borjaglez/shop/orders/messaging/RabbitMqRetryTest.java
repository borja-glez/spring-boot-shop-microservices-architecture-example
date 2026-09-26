package com.borjaglez.shop.orders.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqCommandConsumer;
import com.borjaglez.cqrs.rabbitmq.infrastructure.DefaultRabbitMqNamingStrategy;
import com.borjaglez.shop.contracts.inventory.ReleaseStock;

/** Retry routing of failed commands received over RabbitMQ. */
class RabbitMqRetryTest {

  /** A handler that always fails, as a command that cannot be processed yet. */
  public static class FailingHandler {
    public Object handle(ReleaseStock command) {
      throw new IllegalStateException("database down");
    }
  }

  /**
   * A failed fire-and-forget command is sent to the shared retry exchange with the name of the
   * application that failed as routing key. That application binds its retry queue with its own
   * name, so only it receives the message and, after the TTL, gets it back in its main queue.
   */
  @Test
  void aFailedCommandIsRetriedInTheApplicationThatFailed() throws Exception {
    RabbitTemplate template = mock(RabbitTemplate.class);
    CommandHandlerRegistry registry = new CommandHandlerRegistry();
    String routingKey = "shop.inventory.1.command.stock.release-stock";
    registry.register(
        ReleaseStock.class,
        new FailingHandler(),
        FailingHandler.class.getMethod("handle", ReleaseStock.class),
        routingKey,
        false);
    RabbitMqCommandConsumer consumer =
        new RabbitMqCommandConsumer(
            registry,
            List.of(),
            template,
            new DefaultRabbitMqNamingStrategy("shop"),
            "commands",
            "inventory-service");
    MessageProperties properties = new MessageProperties();
    properties.setReceivedRoutingKey(routingKey);
    properties.setHeader("cqrs.message.type", "command");
    Message message = MessageBuilder.withBody(new byte[0]).andProperties(properties).build();

    consumer.consume(message, new ReleaseStock(UUID.randomUUID()));

    verify(template).send(eq("shop.commands.retry"), eq("inventory-service"), any(Message.class));
  }
}
