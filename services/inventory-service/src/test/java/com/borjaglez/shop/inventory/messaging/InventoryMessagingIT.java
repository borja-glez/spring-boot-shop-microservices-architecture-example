package com.borjaglez.shop.inventory.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.kafka.KafkaEventBus;
import com.borjaglez.cqrs.rabbitmq.RabbitMqCommandBus;
import com.borjaglez.cqrs.rabbitmq.RemoteHandlerException;
import com.borjaglez.shop.contracts.catalog.ProductPublished;
import com.borjaglez.shop.contracts.inventory.ReleaseStock;
import com.borjaglez.shop.contracts.inventory.ReservationLine;
import com.borjaglez.shop.contracts.inventory.ReserveStock;
import com.borjaglez.shop.contracts.inventory.StockRelease;
import com.borjaglez.shop.contracts.inventory.StockReservation;
import com.borjaglez.shop.inventory.InventoryTestSupport;
import com.borjaglez.shop.inventory.domain.StockItemRepository;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;
import com.borjaglez.specrepository.core.Operators;

/**
 * The inventory as the other services see it: products arrive from the catalog over Kafka, and the
 * checkout saga sends its commands over RabbitMQ and waits for the answers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  InventoryTestSupport.class
})
class InventoryMessagingIT {

  @Autowired KafkaEventBus kafka;
  @Autowired RabbitMqCommandBus rabbit;
  @Autowired StockItemRepository stock;
  @Autowired InventoryTestSupport support;

  @Test
  void publishedProductsStartWithTheInitialStock() {
    UUID productId = UUID.randomUUID();

    kafka.publish(
        new ProductPublished(
            productId, "KAF-INV", "Café de Kenia", new BigDecimal("9.90"), "EUR", "seller-ana"));

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(stock.query().where("productId", Operators.EQUALS, productId).findOne())
                    .hasValueSatisfying(item -> assertThat(item.getOnHand()).isEqualTo(25)));
  }

  @Test
  void theSagaReservesAndReleasesOverRabbitMq() {
    ReservationLine coffee = support.product(10, 4);
    UUID orderId = UUID.randomUUID();

    StockReservation reservation =
        rabbit.dispatchAndReceive(new ReserveStock(orderId, List.of(coffee)));
    StockRelease release = rabbit.dispatchAndReceive(new ReleaseStock(orderId));

    assertThat(reservation.reserved()).isTrue();
    assertThat(release.released()).isTrue();
    assertThat(support.item(coffee.productId()).getReserved()).isZero();
  }

  @Test
  void shortagesTravelBackAsAnAnswer() {
    ReservationLine oil = support.product(1, 3);

    StockReservation reservation =
        rabbit.dispatchAndReceive(new ReserveStock(UUID.randomUUID(), List.of(oil)));

    assertThat(reservation.reserved()).isFalse();
    assertThat(reservation.shortages())
        .singleElement()
        .satisfies(s -> assertThat(s.available()).isEqualTo(1));
  }

  @Test
  void anInvalidCommandFailsOnTheCallerSide() {
    assertThatThrownBy(
            () -> rabbit.dispatchAndReceive(new ReserveStock(UUID.randomUUID(), List.of())))
        .isInstanceOfSatisfying(
            RemoteHandlerException.class,
            e ->
                assertThat(e.getRemoteExceptionType())
                    .isEqualTo(IllegalArgumentException.class.getName()));
  }
}
