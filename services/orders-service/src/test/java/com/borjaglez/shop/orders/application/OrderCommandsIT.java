package com.borjaglez.shop.orders.application;

import static com.borjaglez.shop.orders.OrdersTestSupport.discontinued;
import static com.borjaglez.shop.orders.OrdersTestSupport.published;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.shop.contracts.orders.OrderCancelled;
import com.borjaglez.shop.contracts.orders.OrderPlaced;
import com.borjaglez.shop.eskit.AggregateStore;
import com.borjaglez.shop.eskit.ConcurrencyConflictException;
import com.borjaglez.shop.eskit.EventStore;
import com.borjaglez.shop.eskit.RecordedEvent;
import com.borjaglez.shop.orders.application.command.CancelOrderCommand;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand.Item;
import com.borjaglez.shop.orders.application.projection.CatalogProductProjector;
import com.borjaglez.shop.orders.checkout.CheckoutDriver;
import com.borjaglez.shop.orders.checkout.FakeCheckout;
import com.borjaglez.shop.orders.domain.Order;
import com.borjaglez.shop.support.error.BusinessRuleViolationException;
import com.borjaglez.shop.support.error.NotFoundException;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;

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
  CheckoutDriver.class
})
class OrderCommandsIT {

  @Autowired CommandBus commands;
  @Autowired CatalogProductProjector catalog;
  @Autowired EventStore eventStore;
  @Autowired CheckoutDriver checkout;
  @Autowired AggregateStore<Order> orders;

  private List<RecordedEvent> history(UUID orderId) {
    return eventStore.load("order", orderId.toString());
  }

  private UUID productAt(String price) {
    UUID id = UUID.randomUUID();
    catalog.on(published(id, "SKU-" + id.toString().substring(0, 4), price));
    return id;
  }

  @Test
  void placingAnOrderStoresItWithTheCatalogPrices() {
    UUID coffee = productAt("5.00");
    UUID oil = productAt("2.50");

    UUID orderId =
        commands.dispatchAndReceive(
            new PlaceOrderCommand("cliente-lucia", List.of(new Item(coffee, 3), new Item(oil, 2))));

    assertThat(history(orderId))
        .singleElement()
        .satisfies(
            recorded -> {
              assertThat(recorded.version()).isEqualTo(1);
              OrderPlaced placed = (OrderPlaced) recorded.event();
              assertThat(placed.getTotal()).isEqualByComparingTo("20.00");
              assertThat(placed.getLines()).hasSize(2);
            });
  }

  @Test
  void unknownProductsCannotBeOrdered() {
    assertThatThrownBy(
            () ->
                commands.dispatchAndReceive(
                    new PlaceOrderCommand(
                        "cliente-lucia", List.of(new Item(UUID.randomUUID(), 1)))))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "product-unavailable");
  }

  @Test
  void discontinuedProductsCannotBeOrdered() {
    UUID product = productAt("5.00");
    catalog.on(discontinued(product));

    assertThatThrownBy(
            () ->
                commands.dispatchAndReceive(
                    new PlaceOrderCommand("cliente-lucia", List.of(new Item(product, 1)))))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "product-unavailable");
  }

  @Test
  void theCustomerCanCancelTheirOrder() {
    UUID orderId =
        commands.dispatchAndReceive(
            new PlaceOrderCommand("cliente-lucia", List.of(new Item(productAt("5.00"), 1))));
    checkout.finish(orderId);

    commands.dispatchAndWait(new CancelOrderCommand(orderId, "cliente-lucia", "Ya no lo necesito"));

    assertThat(history(orderId))
        .extracting(RecordedEvent::event)
        .last()
        .isInstanceOf(OrderCancelled.class);
  }

  @Test
  void otherCustomersCannotSeeOrCancelTheOrder() {
    UUID orderId =
        commands.dispatchAndReceive(
            new PlaceOrderCommand("cliente-lucia", List.of(new Item(productAt("5.00"), 1))));

    assertThatThrownBy(
            () -> commands.dispatchAndWait(new CancelOrderCommand(orderId, "cliente-mateo", "x")))
        .isInstanceOf(NotFoundException.class)
        .hasFieldOrPropertyWithValue("code", "order-not-found");
    assertThat(history(orderId)).hasSize(1);
  }

  @Test
  void unknownOrdersAreNotFound() {
    assertThatThrownBy(
            () ->
                commands.dispatchAndWait(
                    new CancelOrderCommand(UUID.randomUUID(), "cliente-lucia", "x")))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void aStaleCopyOfTheOrderCannotOverwriteANewerOne() {
    UUID coffee = productAt("5.00");
    UUID orderId =
        commands.dispatchAndReceive(
            new PlaceOrderCommand("cliente-lucia", List.of(new Item(coffee, 1))));
    checkout.finish(orderId);
    Order first = orders.load(orderId.toString()).orElseThrow();
    Order second = orders.load(orderId.toString()).orElseThrow();

    first.cancel("cliente-lucia", "Primero");
    orders.save(first);
    second.cancel("cliente-lucia", "Segundo");

    assertThatThrownBy(() -> orders.save(second))
        .isInstanceOf(ConcurrencyConflictException.class)
        .extracting("code")
        .isEqualTo("concurrent-modification");
    assertThat(history(orderId)).hasSize(3);
  }
}
