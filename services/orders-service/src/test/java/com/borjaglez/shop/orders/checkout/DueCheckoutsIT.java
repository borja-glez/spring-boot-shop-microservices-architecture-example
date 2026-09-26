package com.borjaglez.shop.orders.checkout;

import static com.borjaglez.shop.orders.OrdersTestSupport.published;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.shop.orders.application.checkout.DueCheckouts;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand.Item;
import com.borjaglez.shop.orders.application.projection.CatalogProductProjector;
import com.borjaglez.shop.orders.domain.CheckoutSaga;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Several runners, one per instance of the service, share the checkouts: each due saga goes to
 * exactly one of them, and comes back only when its lease runs out.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "shop.checkout.enabled=false")
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  FakeCheckout.class,
  CheckoutDriver.class
})
class DueCheckoutsIT {

  private static final int ALL = 1_000;

  @Autowired CommandBus commands;
  @Autowired CatalogProductProjector catalog;
  @Autowired DueCheckouts dueCheckouts;
  @Autowired CheckoutDriver driver;
  @Autowired MeterRegistry meters;

  private UUID place() {
    UUID productId = UUID.randomUUID();
    catalog.on(published(productId, "DUE-" + productId.toString().substring(0, 4), "3.00"));
    return commands.dispatchAndReceive(
        new PlaceOrderCommand("cliente-lucia", List.of(new Item(productId, 1))));
  }

  @Test
  void concurrentRunnersNeverClaimTheSameSaga() throws Exception {
    List<UUID> placed = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      placed.add(place());
    }
    OffsetDateTime now = OffsetDateTime.now().plusSeconds(1);

    List<UUID> claimed = new ArrayList<>();
    try (ExecutorService runners = Executors.newFixedThreadPool(4)) {
      List<Callable<List<UUID>>> claims = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        claims.add(() -> dueCheckouts.claim(5, now, now.plusSeconds(30)));
      }
      for (int round = 0; round < 10; round++) {
        for (Future<List<UUID>> result : runners.invokeAll(claims)) {
          claimed.addAll(result.get());
        }
      }
    }

    assertThat(claimed).doesNotHaveDuplicates().containsAll(placed);
  }

  @Test
  void aClaimedSagaComesBackOnlyWhenItsLeaseRunsOut() {
    UUID orderId = place();
    OffsetDateTime now = OffsetDateTime.now().plusSeconds(1);

    assertThat(dueCheckouts.claim(ALL, now, now.plusSeconds(30))).contains(orderId);
    assertThat(dueCheckouts.claim(ALL, now.plusSeconds(10), now.plusSeconds(40)))
        .doesNotContain(orderId);
    assertThat(dueCheckouts.claim(ALL, now.plusSeconds(31), now.plusSeconds(61))).contains(orderId);
  }

  @Test
  void aFinishedCheckoutIsCountedAndTimed() {
    double before = confirmed();

    CheckoutSaga saga = driver.finish(place());

    assertThat(saga.getState()).isEqualTo(CheckoutSaga.State.COMPLETED);
    assertThat(confirmed()).isEqualTo(before + 1);
    assertThat(meters.get("shop.checkout.duration").tag("outcome", "confirmed").timer().count())
        .isPositive();
    assertThat(
            meters
                .get("shop.checkout.steps")
                .tags("step", "RESERVE_STOCK", "result", "succeeded")
                .counter()
                .count())
        .isPositive();
  }

  private double confirmed() {
    var counter =
        meters
            .find("shop.checkout.completed")
            .tags("outcome", "confirmed", "reason", "none")
            .counter();
    return counter == null ? 0 : counter.count();
  }
}
