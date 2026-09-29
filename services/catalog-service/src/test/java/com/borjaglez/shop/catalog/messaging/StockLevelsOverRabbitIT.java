package com.borjaglez.shop.catalog.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.catalog.application.query.StockLevelsGateway;
import com.borjaglez.shop.contracts.inventory.GetStockLevels;
import com.borjaglez.shop.contracts.inventory.StockLevel;
import com.borjaglez.shop.contracts.inventory.StockLevels;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;

/**
 * The stock gateway over a real RabbitMQ. The inventory's handler lives in this test context, so
 * every query travels through the broker and back as it would between services.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "shop.remote-queries.reply-timeout=500ms")
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  StockLevelsOverRabbitIT.RemoteInventory.class
})
class StockLevelsOverRabbitIT {

  static final UUID SLOW = UUID.randomUUID();
  static final UUID BROKEN = UUID.randomUUID();

  /** Stand-in for inventory-service, reached over RabbitMQ. */
  @TestConfiguration(proxyBeanMethods = false)
  @QueryHandler
  static class RemoteInventory {

    @HandleQuery
    public StockLevels levels(GetStockLevels query) {
      if (query.getProductIds().contains(BROKEN)) {
        throw new IllegalStateException("warehouse on fire");
      }
      if (query.getProductIds().contains(SLOW)) {
        sleep(1500);
      }
      return new StockLevels(
          query.getProductIds().stream().map(id -> new StockLevel(id, 7)).toList());
    }

    private static void sleep(long millis) {
      try {
        Thread.sleep(millis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Autowired StockLevelsGateway stock;

  @Test
  void theQueryTravelsOverRabbitMq() {
    UUID productId = UUID.randomUUID();

    assertThat(stock.available(List.of(productId)))
        .hasValueSatisfying(units -> assertThat(units).containsEntry(productId, 7).hasSize(1));
  }

  @Test
  void aSlowInventoryLeavesTheStockUnknown() {
    assertThat(stock.available(List.of(SLOW))).isEmpty();
  }

  @Test
  void aFailingInventoryLeavesTheStockUnknown() {
    assertThat(stock.available(List.of(BROKEN))).isEmpty();
  }
}
