package com.borjaglez.shop.reporting.application.projection;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.shop.contracts.orders.OrderCancelled;
import com.borjaglez.shop.contracts.orders.OrderConfirmed;
import com.borjaglez.shop.contracts.orders.OrderLine;
import com.borjaglez.shop.contracts.orders.OrderPlaced;
import com.borjaglez.shop.contracts.orders.OrderRejected;
import com.borjaglez.shop.eskit.IdempotentConsumer;
import com.borjaglez.shop.reporting.domain.ReportLine;
import com.borjaglez.shop.reporting.domain.ReportLineRepository;
import com.borjaglez.shop.reporting.domain.ReportOrder;
import com.borjaglez.shop.reporting.domain.ReportOrderRepository;
import com.borjaglez.specrepository.core.Operators;

/**
 * Builds the report tables from the order events. Each event is applied once (es-kit's {@link
 * IdempotentConsumer}) and in any order across types. Reading the whole topic again from offset 0
 * after clearing the tables gives the same numbers: that is how a rebuild works.
 */
@EventHandler
public class ReportProjector {

  public static final String CONSUMER = "reporting.orders";

  private final ReportOrderRepository orders;
  private final ReportLineRepository lines;
  private final IdempotentConsumer idempotent;

  public ReportProjector(
      ReportOrderRepository orders, ReportLineRepository lines, IdempotentConsumer idempotent) {
    this.orders = orders;
    this.lines = lines;
    this.idempotent = idempotent;
  }

  @HandleEvent
  public void on(OrderPlaced event) {
    apply(
        event,
        event.getOrderId(),
        order -> {
          if (order.isPlaced()) {
            return;
          }
          order.placed(
              event.getCustomerId(),
              event.getTotal(),
              event.getCurrency(),
              event.getLines().size(),
              at(event));
          ReportOrder saved = orders.save(order);
          for (OrderLine line : event.getLines()) {
            lines.save(
                new ReportLine(
                    saved,
                    line.productId(),
                    line.sku(),
                    line.name(),
                    line.quantity(),
                    line.unitPrice()));
          }
        });
  }

  @HandleEvent
  public void on(OrderConfirmed event) {
    apply(event, event.getOrderId(), order -> save(order, o -> o.confirmed(at(event))));
  }

  @HandleEvent
  public void on(OrderRejected event) {
    apply(
        event,
        event.getOrderId(),
        order -> save(order, o -> o.rejected(event.getReason(), at(event))));
  }

  @HandleEvent
  public void on(OrderCancelled event) {
    apply(event, event.getOrderId(), order -> save(order, o -> o.cancelled(at(event))));
  }

  private void save(ReportOrder order, Consumer<ReportOrder> change) {
    change.accept(order);
    orders.save(order);
  }

  private void apply(Event event, UUID orderId, Consumer<ReportOrder> change) {
    idempotent.once(
        CONSUMER,
        event,
        () ->
            change.accept(
                orders
                    .query()
                    .where("orderId", Operators.EQUALS, orderId)
                    .findOne()
                    .orElseGet(() -> ReportOrder.unknown(orderId))));
  }

  private static OffsetDateTime at(Event event) {
    return event.getOccurredOn().atOffset(ZoneOffset.UTC);
  }
}
