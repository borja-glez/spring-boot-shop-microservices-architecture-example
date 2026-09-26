package com.borjaglez.shop.orders.checkout;

import static com.borjaglez.shop.orders.OrdersTestSupport.published;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.shop.eskit.EventStore;
import com.borjaglez.shop.eskit.StoredEvent;
import com.borjaglez.shop.eskit.StoredEventRepository;
import com.borjaglez.shop.orders.application.checkout.CheckoutSagaRunner;
import com.borjaglez.shop.orders.application.command.CancelOrderCommand;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand.Item;
import com.borjaglez.shop.orders.application.projection.CatalogProductProjector;
import com.borjaglez.shop.orders.domain.CheckoutSaga;
import com.borjaglez.shop.support.tracing.TraceCarrier;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;
import com.borjaglez.specrepository.core.Operators;

/**
 * One order is one trace: the saga keeps the trace of the request that placed or cancelled the
 * order, every step continues it, and the events those steps record carry it to the relay.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "shop.checkout.enabled=false",
      "shop.checkout.initial-backoff=0s",
      "shop.checkout.max-backoff=0s"
    })
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  FakeCheckout.class,
  CheckoutDriver.class,
  CheckoutTracingIT.Traces.class
})
class CheckoutTracingIT {

  /** A trace per thread, standing in for the current span; records every resumed trace. */
  static class RecordingTraceCarrier implements TraceCarrier {
    final ThreadLocal<String> current = new ThreadLocal<>();
    final List<String> resumed = new CopyOnWriteArrayList<>();

    @Override
    public Map<String, String> capture() {
      String trace = current.get();
      return trace == null ? Map.of() : Map.of(TRACEPARENT, trace);
    }

    @Override
    public <T> T resume(Map<String, String> captured, String name, Supplier<T> work) {
      String trace = captured.getOrDefault(TRACEPARENT, "new");
      resumed.add(name + " <- " + trace);
      String previous = current.get();
      current.set(trace);
      try {
        return work.get();
      } finally {
        current.set(previous);
      }
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Traces {
    @Bean
    RecordingTraceCarrier recordingTraceCarrier() {
      return new RecordingTraceCarrier();
    }
  }

  private static final String PLACING = "00-11111111111111111111111111111111-1111111111111111-01";
  private static final String CANCELLING =
      "00-22222222222222222222222222222222-2222222222222222-01";

  @Autowired CommandBus commands;
  @Autowired CatalogProductProjector catalog;
  @Autowired EventStore eventStore;
  @Autowired StoredEventRepository storedEvents;
  @Autowired CheckoutDriver driver;
  @Autowired CheckoutSagaRunner runner;
  @Autowired FakeCheckout fakes;
  @Autowired RecordingTraceCarrier traces;

  @AfterEach
  void clear() {
    traces.current.remove();
    traces.resumed.clear();
  }

  private UUID placeIn(String trace) {
    UUID productId = UUID.randomUUID();
    catalog.on(published(productId, "TRC-" + productId.toString().substring(0, 4), "5.00"));
    traces.current.set(trace);
    try {
      return commands.dispatchAndReceive(
          new PlaceOrderCommand("cliente-lucia", List.of(new Item(productId, 1))));
    } finally {
      traces.current.remove();
    }
  }

  private String traceOf(UUID orderId, String eventType) {
    StoredEvent row =
        storedEvents
            .query()
            .where("streamId", Operators.EQUALS, orderId.toString())
            .where("eventType", Operators.ENDS_WITH, eventType)
            .findOne()
            .orElseThrow();
    return eventStore.metadata(row).get(TraceCarrier.TRACEPARENT);
  }

  @Test
  void everyStepAndEveryEventBelongToTheTraceThatPlacedTheOrder() {
    UUID orderId = placeIn(PLACING);

    CheckoutSaga saga = driver.finish(orderId);

    assertThat(saga.getTraceParent()).isEqualTo(PLACING);
    assertThat(traces.resumed)
        .containsExactly(
            "checkout RESERVE_STOCK <- " + PLACING,
            "checkout AUTHORIZE_PAYMENT <- " + PLACING,
            "checkout CONFIRM_ORDER <- " + PLACING);
    assertThat(traceOf(orderId, "order-placed")).isEqualTo(PLACING);
    // Recorded by a local step, on the runner's thread: still the same trace.
    assertThat(traceOf(orderId, "order-confirmed")).isEqualTo(PLACING);
  }

  @Test
  void aCancellationStartsTheRefundInTheTraceOfTheCancelRequest() {
    UUID orderId = placeIn(PLACING);
    driver.finish(orderId);
    traces.resumed.clear();

    traces.current.set(CANCELLING);
    commands.dispatchAndWait(new CancelOrderCommand(orderId, "cliente-lucia", "Me equivoqué"));
    traces.current.remove();
    CheckoutSaga saga = driver.finish(orderId);

    assertThat(saga.getTraceParent()).isEqualTo(CANCELLING);
    assertThat(traces.resumed).allSatisfy(step -> assertThat(step).endsWith(CANCELLING));
  }

  @Test
  void anErrorInAStepIsLoggedAndTheRunnerCarriesOn() {
    UUID orderId = placeIn(PLACING);
    fakes.script(orderId).inventoryThrowsErrors();

    assertThat(runner.runDue()).isZero();

    assertThat(driver.saga(orderId).getState()).isEqualTo(CheckoutSaga.State.RUNNING);
  }
}
