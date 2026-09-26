package com.borjaglez.shop.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.shop.contracts.inventory.ReleaseStock;
import com.borjaglez.shop.contracts.inventory.ReservationLine;
import com.borjaglez.shop.contracts.inventory.ReserveStock;
import com.borjaglez.shop.contracts.inventory.StockRelease;
import com.borjaglez.shop.contracts.inventory.StockReservation;
import com.borjaglez.shop.contracts.inventory.StockShortage;
import com.borjaglez.shop.eskit.StoredEvent;
import com.borjaglez.shop.eskit.StoredEventRepository;
import com.borjaglez.shop.inventory.InventoryTestSupport;
import com.borjaglez.shop.inventory.application.command.AdjustStockCommand;
import com.borjaglez.shop.support.error.BusinessRuleViolationException;
import com.borjaglez.shop.support.error.NotFoundException;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;
import com.borjaglez.specrepository.core.Operators;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  InventoryTestSupport.class
})
class InventoryCommandsIT {

  @Autowired CommandBus commands;
  @Autowired InventoryTestSupport support;
  @Autowired StoredEventRepository outbox;

  private StockReservation reserve(UUID orderId, ReservationLine... lines) {
    return commands.dispatchAndReceive(new ReserveStock(orderId, List.of(lines)));
  }

  private StockRelease release(UUID orderId) {
    return commands.dispatchAndReceive(new ReleaseStock(orderId));
  }

  private List<String> outboxTypes(UUID orderId) {
    return outbox.query().where("streamId", Operators.EQUALS, orderId.toString()).findAll().stream()
        .map(StoredEvent::getEventType)
        .toList();
  }

  @Test
  void aReservationHoldsEveryLine() {
    ReservationLine coffee = support.product(10, 3);
    ReservationLine oil = support.product(5, 5);
    UUID orderId = UUID.randomUUID();

    StockReservation result = reserve(orderId, coffee, oil);

    assertThat(result.reserved()).isTrue();
    assertThat(result.shortages()).isEmpty();
    assertThat(support.item(coffee.productId()).available()).isEqualTo(7);
    assertThat(support.item(oil.productId()).available()).isZero();
    assertThat(outboxTypes(orderId)).containsExactly("shop.inventory.1.event.stock.stock-reserved");
  }

  @Test
  void aShortageReservesNothing() {
    ReservationLine coffee = support.product(10, 3);
    ReservationLine oil = support.product(2, 5);
    UUID orderId = UUID.randomUUID();

    StockReservation result = reserve(orderId, coffee, oil);

    assertThat(result.reserved()).isFalse();
    assertThat(result.shortages())
        .containsExactly(new StockShortage(oil.productId(), oil.sku(), 5, 2));
    assertThat(support.item(coffee.productId()).getReserved()).isZero();
    assertThat(outboxTypes(orderId)).isEmpty();
  }

  @Test
  void aProductTheInventoryDoesNotKnowYetIsATechnicalFailure() {
    ReservationLine coffee = support.product(10, 3);
    ReservationLine unknown = new ReservationLine(UUID.randomUUID(), "NOPE-1", 1);
    UUID orderId = UUID.randomUUID();

    assertThatThrownBy(() -> reserve(orderId, coffee, unknown))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("NOPE-1");
    assertThat(support.item(coffee.productId()).getReserved()).isZero();
    assertThat(outboxTypes(orderId)).isEmpty();
  }

  @Test
  void repeatedLinesAddUp() {
    ReservationLine coffee = support.product(5, 3);

    StockReservation result = reserve(UUID.randomUUID(), coffee, coffee);

    assertThat(result.shortages())
        .containsExactly(new StockShortage(coffee.productId(), coffee.sku(), 6, 5));
  }

  @Test
  void reservingTwiceForTheSameOrderHoldsTheStockOnce() {
    ReservationLine coffee = support.product(10, 4);
    UUID orderId = UUID.randomUUID();

    reserve(orderId, coffee);
    StockReservation retry = reserve(orderId, coffee);

    assertThat(retry.reserved()).isTrue();
    assertThat(support.item(coffee.productId()).getReserved()).isEqualTo(4);
  }

  @Test
  void releasingGivesTheStockBackOnce() {
    ReservationLine coffee = support.product(10, 4);
    UUID orderId = UUID.randomUUID();
    reserve(orderId, coffee);

    assertThat(release(orderId).released()).isTrue();
    assertThat(release(orderId).released()).isFalse();

    assertThat(support.item(coffee.productId()).getReserved()).isZero();
    assertThat(outboxTypes(orderId))
        .containsExactly(
            "shop.inventory.1.event.stock.stock-reserved",
            "shop.inventory.1.event.stock.stock-released");
  }

  @Test
  void aReservationArrivingAfterTheReleaseHoldsNothing() {
    ReservationLine coffee = support.product(10, 4);
    UUID orderId = UUID.randomUUID();

    assertThat(release(orderId).released()).isFalse();
    StockReservation late = reserve(orderId, coffee);

    assertThat(late.reserved()).isFalse();
    assertThat(support.item(coffee.productId()).getReserved()).isZero();
  }

  @Test
  void invalidReservationsAreRejected() {
    assertThatThrownBy(() -> reserve(UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
    ReservationLine none = support.product(10, 0);
    assertThatThrownBy(() -> reserve(UUID.randomUUID(), none))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void countingStockKeepsTheReservedUnits() {
    ReservationLine coffee = support.product(10, 4);
    reserve(UUID.randomUUID(), coffee);

    commands.dispatchAndWait(new AdjustStockCommand(coffee.productId(), 6, "almacen"));

    assertThat(support.item(coffee.productId()).available()).isEqualTo(2);
    assertThatThrownBy(
            () ->
                commands.dispatchAndWait(new AdjustStockCommand(coffee.productId(), 3, "almacen")))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThatThrownBy(
            () -> commands.dispatchAndWait(new AdjustStockCommand(UUID.randomUUID(), 3, "almacen")))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void concurrentReservationsOfTheSameProductAllGoThroughWhenThereIsStock() throws Exception {
    ReservationLine unit = support.product(30, 1);
    List<UUID> orders = java.util.stream.Stream.generate(UUID::randomUUID).limit(20).toList();

    List<StockReservation> results;
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = orders.stream().map(id -> executor.submit(() -> reserve(id, unit))).toList();
      results = new java.util.ArrayList<>();
      for (var future : futures) {
        results.add(future.get());
      }
    }

    assertThat(results).allMatch(StockReservation::reserved);
    assertThat(support.item(unit.productId()).available()).isEqualTo(10);
  }

  @Test
  void concurrentReservationsNeverTakeMoreThanThereIs() throws Exception {
    ReservationLine unit = support.product(5, 1);
    List<UUID> orders = java.util.stream.Stream.generate(UUID::randomUUID).limit(20).toList();

    long reserved;
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = orders.stream().map(id -> executor.submit(() -> reserve(id, unit))).toList();
      reserved = 0;
      for (var future : futures) {
        reserved += future.get().reserved() ? 1 : 0;
      }
    }

    assertThat(reserved).isEqualTo(5);
    assertThat(support.item(unit.productId()).available()).isZero();
  }

  @Test
  void reservationsCrossingTheSameProductsInOppositeOrderAllGoThrough() throws Exception {
    ReservationLine first = support.product(40, 1);
    ReservationLine second = support.product(40, 1);
    List<UUID> orders = java.util.stream.Stream.generate(UUID::randomUUID).limit(20).toList();

    List<StockReservation> results;
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var futures =
          java.util.stream.IntStream.range(0, orders.size())
              .mapToObj(
                  i ->
                      executor.submit(
                          () ->
                              i % 2 == 0
                                  ? reserve(orders.get(i), first, second)
                                  : reserve(orders.get(i), second, first)))
              .toList();
      results = new java.util.ArrayList<>();
      for (var future : futures) {
        results.add(future.get());
      }
    }

    assertThat(results).allMatch(StockReservation::reserved);
    assertThat(support.item(first.productId()).available()).isEqualTo(20);
    assertThat(support.item(second.productId()).available()).isEqualTo(20);
  }
}
