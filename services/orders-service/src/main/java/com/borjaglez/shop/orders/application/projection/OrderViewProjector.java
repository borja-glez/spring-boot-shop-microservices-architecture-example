package com.borjaglez.shop.orders.application.projection;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.shop.contracts.orders.OrderCancelled;
import com.borjaglez.shop.contracts.orders.OrderConfirmed;
import com.borjaglez.shop.contracts.orders.OrderPlaced;
import com.borjaglez.shop.contracts.orders.OrderRejected;
import com.borjaglez.shop.eskit.IdempotentConsumer;
import com.borjaglez.shop.orders.domain.OrderView;
import com.borjaglez.shop.orders.domain.OrderViewLine;
import com.borjaglez.shop.orders.domain.OrderViewRepository;
import com.borjaglez.specrepository.core.Operators;

/** Builds the order read model from the order events received over Kafka. */
@EventHandler
public class OrderViewProjector {

  static final String CONSUMER = "orders.order-view";

  private final OrderViewRepository views;
  private final IdempotentConsumer idempotent;

  public OrderViewProjector(OrderViewRepository views, IdempotentConsumer idempotent) {
    this.views = views;
    this.idempotent = idempotent;
  }

  @HandleEvent
  public void on(OrderPlaced event) {
    apply(
        event,
        event.getOrderId(),
        view ->
            view.placed(
                event.getCustomerId(),
                event.getLines().stream()
                    .map(
                        l ->
                            new OrderViewLine(
                                l.productId(), l.sku(), l.name(), l.quantity(), l.unitPrice()))
                    .toList(),
                event.getTotal(),
                event.getCurrency(),
                at(event)));
  }

  @HandleEvent
  public void on(OrderConfirmed event) {
    apply(event, event.getOrderId(), view -> view.confirmed(event.getPaymentId(), at(event)));
  }

  @HandleEvent
  public void on(OrderRejected event) {
    apply(
        event,
        event.getOrderId(),
        view -> view.rejected(event.getReason(), event.getDetail(), at(event)));
  }

  @HandleEvent
  public void on(OrderCancelled event) {
    apply(event, event.getOrderId(), view -> view.cancelled(event.getReason(), at(event)));
  }

  private void apply(Event event, UUID orderId, Consumer<OrderView> change) {
    idempotent.once(
        CONSUMER,
        event,
        () -> {
          OrderView view =
              views
                  .query()
                  .where("orderId", Operators.EQUALS, orderId)
                  .findOne()
                  .orElseGet(() -> OrderView.unknown(orderId));
          change.accept(view);
          views.save(view);
        });
  }

  private static OffsetDateTime at(Event event) {
    return event.getOccurredOn().atOffset(ZoneOffset.UTC);
  }
}
