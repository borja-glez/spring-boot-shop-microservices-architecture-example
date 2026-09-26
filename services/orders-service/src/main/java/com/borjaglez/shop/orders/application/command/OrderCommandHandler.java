package com.borjaglez.shop.orders.application.command;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.shop.eskit.AggregateStore;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand.Item;
import com.borjaglez.shop.orders.domain.CatalogProduct;
import com.borjaglez.shop.orders.domain.CatalogProductRepository;
import com.borjaglez.shop.orders.domain.CheckoutSaga;
import com.borjaglez.shop.orders.domain.CheckoutSagaRepository;
import com.borjaglez.shop.orders.domain.Order;
import com.borjaglez.shop.orders.domain.PricedLine;
import com.borjaglez.shop.support.error.BusinessRuleViolationException;
import com.borjaglez.shop.support.error.NotFoundException;
import com.borjaglez.shop.support.tracing.TraceCarrier;
import com.borjaglez.specrepository.core.Operators;

/**
 * Write side of orders. Each command loads the order from its events, decides, and appends the new
 * events; the store rejects the write if another request changed the order in the meantime.
 *
 * <p>Placing an order also starts its checkout saga, and cancelling a confirmed order turns the
 * saga into a refund; both in the same transaction as the order's event, so the saga exists exactly
 * when the event does.
 */
@CommandHandler
public class OrderCommandHandler {

  private final AggregateStore<Order> orders;
  private final CatalogProductRepository catalog;
  private final CheckoutSagaRepository sagas;
  private final Clock clock;
  private final TraceCarrier traces;

  public OrderCommandHandler(
      AggregateStore<Order> orders,
      CatalogProductRepository catalog,
      CheckoutSagaRepository sagas,
      Clock clock,
      TraceCarrier traces) {
    this.orders = orders;
    this.catalog = catalog;
    this.sagas = sagas;
    this.clock = clock;
    this.traces = traces;
  }

  @HandleCommand
  @Transactional
  public UUID place(PlaceOrderCommand command) {
    Order order =
        Order.place(UUID.randomUUID(), command.getCustomerId(), price(command.getItems()));
    orders.save(order);
    UUID orderId = UUID.fromString(order.id());
    CheckoutSaga saga =
        CheckoutSaga.start(orderId, command.getCustomerId(), OffsetDateTime.now(clock));
    saga.followTrace(traces.capture().get(TraceCarrier.TRACEPARENT));
    sagas.save(saga);
    return orderId;
  }

  @HandleCommand
  @Transactional
  public void cancel(CancelOrderCommand command) {
    Order order =
        orders
            .load(command.getOrderId().toString())
            .filter(o -> o.belongsTo(command.getCustomerId()))
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "order-not-found", "Unknown order " + command.getOrderId()));
    order.cancel(command.getCustomerId(), command.getReason());
    if (order.pendingChanges().isEmpty()) {
      return; // Already cancelled.
    }
    orders.save(order);
    CheckoutSaga saga =
        sagas
            .query()
            .where("orderId", Operators.EQUALS, command.getOrderId())
            .findOne()
            .orElseThrow(
                () -> new IllegalStateException("No checkout saga for " + command.getOrderId()));
    saga.cancel(OffsetDateTime.now(clock));
    saga.followTrace(traces.capture().get(TraceCarrier.TRACEPARENT));
    sagas.save(saga);
  }

  /** Prices the items with the local catalog projection; every product must be orderable. */
  private List<PricedLine> price(List<Item> items) {
    List<UUID> ids = items.stream().map(Item::productId).distinct().toList();
    Map<UUID, CatalogProduct> known =
        catalog.query().where("productId", Operators.IN, ids).findAll().stream()
            .filter(CatalogProduct::isOrderable)
            .collect(Collectors.toMap(CatalogProduct::getProductId, Function.identity()));
    return items.stream()
        .map(
            item -> {
              CatalogProduct product = known.get(item.productId());
              if (product == null) {
                throw new BusinessRuleViolationException(
                    "product-unavailable", "Product " + item.productId() + " is not for sale");
              }
              return new PricedLine(
                  product.getProductId(),
                  product.getSku(),
                  product.getName(),
                  item.quantity(),
                  product.getPrice(),
                  product.getCurrency());
            })
        .toList();
  }
}
