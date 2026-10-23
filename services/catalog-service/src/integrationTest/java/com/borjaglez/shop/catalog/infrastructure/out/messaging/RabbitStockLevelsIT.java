package com.borjaglez.shop.catalog.infrastructure.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration;
import com.borjaglez.cqrs.autoconfigure.CqrsSerializationAutoConfiguration;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.cqrs.rabbitmq.config.RabbitMqCqrsAutoConfiguration;
import com.borjaglez.cqrs.rabbitmq.config.RabbitMqQueryBusAutoConfiguration;
import com.borjaglez.shop.catalog.application.port.out.StockLevels;
import com.borjaglez.shop.catalog.domain.ProductId;
import com.borjaglez.shop.catalog.infrastructure.config.StockLevelsConfiguration;
import com.borjaglez.shop.contracts.inventory.GetStockLevels;
import com.borjaglez.shop.contracts.inventory.StockLevel;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;

/**
 * The stock levels asked to the inventory over a real RabbitMQ, as the catalog wires them. The
 * inventory is a stand-in that lives in this test context, so every question travels through the
 * broker and back as it does between the services. Only RabbitMQ and spring-boot-cqrs are started;
 * the reply timeout is {@code REMOTE_QUERY_TIMEOUT}, as in production.
 */
@SpringBootTest(
    classes = RabbitStockLevelsIT.InventoryOverRabbitMq.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "REMOTE_QUERY_TIMEOUT=" + RabbitStockLevelsIT.TIMEOUT_MILLIS + "ms")
class RabbitStockLevelsIT {

  static final long TIMEOUT_MILLIS = 500;
  private static final Duration TIMEOUT = Duration.ofMillis(TIMEOUT_MILLIS);

  private static final ProductId IN_STOCK = new ProductId(UUID.randomUUID());
  private static final ProductId SOLD_OUT = new ProductId(UUID.randomUUID());
  private static final ProductId UNKNOWN = new ProductId(UUID.randomUUID());
  static final UUID BROKEN = UUID.randomUUID();

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration({
    ServiceConnectionAutoConfiguration.class,
    JacksonAutoConfiguration.class,
    RabbitAutoConfiguration.class,
    CqrsAutoConfiguration.class,
    CqrsSerializationAutoConfiguration.class,
    RabbitMqCqrsAutoConfiguration.class,
    RabbitMqQueryBusAutoConfiguration.class
  })
  @Import({RabbitTestConfiguration.class, StockLevelsConfiguration.class, FakeInventory.class})
  static class InventoryOverRabbitMq {}

  /**
   * Stand-in for inventory-service, reached over RabbitMQ. It knows two products and fails when
   * asked about {@link #BROKEN}.
   */
  @QueryHandler
  static class FakeInventory {

    @HandleQuery
    public com.borjaglez.shop.contracts.inventory.StockLevels levels(GetStockLevels query) {
      if (query.getProductIds().contains(BROKEN)) {
        throw new IllegalStateException("warehouse on fire");
      }
      Map<UUID, Integer> known = Map.of(IN_STOCK.value(), 7, SOLD_OUT.value(), 0);
      return new com.borjaglez.shop.contracts.inventory.StockLevels(
          query.getProductIds().stream()
              .filter(known::containsKey)
              .map(id -> new StockLevel(id, known.get(id)))
              .toList());
    }
  }

  /** How the inventory lets the catalog down. */
  enum Inventory {
    /** Its consumers are all busy: the question waits in its queue past the timeout. */
    SLOW,
    /** It takes the question and answers with an error. */
    FAILING
  }

  @Autowired StockLevels stock;

  /** The consumers of the inventory stand-in, which answer the stock questions. */
  @Autowired
  @Qualifier("cqrsQueryListenerContainer")
  SimpleMessageListenerContainer inventoryConsumers;

  @Autowired AmqpAdmin broker;

  @Test
  void answersWithLevels() {
    assertThat(stock.available(List.of(IN_STOCK, SOLD_OUT, UNKNOWN)))
        .hasValue(Map.of(IN_STOCK, 7, SOLD_OUT, 0));
  }

  @ParameterizedTest(name = "{0} inventory")
  @EnumSource(Inventory.class)
  void slowOrFailingInventoryGivesUnknown(Inventory inventory) {
    boolean slow = inventory == Inventory.SLOW;
    if (slow) {
      inventoryConsumers.stop();
    }
    try {
      long start = System.nanoTime();

      Optional<Map<ProductId, Integer>> levels =
          stock.available(List.of(slow ? IN_STOCK : new ProductId(BROKEN)));

      Duration waited = Duration.ofNanos(System.nanoTime() - start);
      assertThat(levels).isEmpty();
      // Well under the 1 s the catalog waits when REMOTE_QUERY_TIMEOUT is not set.
      assertThat(waited).isLessThan(TIMEOUT.plusMillis(400));
      if (slow) {
        assertThat(waited).isGreaterThanOrEqualTo(TIMEOUT);
      }
    } finally {
      if (slow) {
        // The question nobody took is dropped, so no answer arrives after the timeout.
        for (String queue : inventoryConsumers.getQueueNames()) {
          broker.purgeQueue(queue, false);
        }
        inventoryConsumers.start();
      }
    }
  }

  @Test
  void anUnreachableBrokerGivesUnknown() {
    QueryBus unreachable =
        new QueryBus() {
          @Override
          public <R> R ask(Query query) {
            throw new AmqpConnectException(new ConnectException("Connection refused"));
          }
        };

    assertThat(new RabbitStockLevels(unreachable).available(List.of(IN_STOCK))).isEmpty();
  }
}
