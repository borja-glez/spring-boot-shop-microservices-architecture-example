package com.borjaglez.shop.orders.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.eskit.StoredEvent;
import com.borjaglez.shop.orders.api.dto.CancelOrderRequest;
import com.borjaglez.shop.orders.api.dto.CreatedResponse;
import com.borjaglez.shop.orders.api.dto.PlaceOrderRequest;
import com.borjaglez.shop.orders.application.command.CancelOrderCommand;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand;
import com.borjaglez.shop.orders.application.query.GetCheckoutQuery;
import com.borjaglez.shop.orders.application.query.GetOrderHistoryQuery;
import com.borjaglez.shop.orders.application.query.GetOrderQuery;
import com.borjaglez.shop.orders.application.query.ListMyOrdersQuery;
import com.borjaglez.shop.orders.application.query.OrderViews.CheckoutView;
import com.borjaglez.shop.orders.application.query.OrderViews.HistoryEntry;
import com.borjaglez.shop.orders.application.query.OrderViews.OrderDetail;
import com.borjaglez.shop.orders.application.query.OrderViews.OrderSummary;
import com.borjaglez.shop.orders.application.query.OrderViews.StoredEventView;
import com.borjaglez.shop.orders.application.query.SearchEventStoreQuery;
import com.borjaglez.shop.orders.domain.OrderView;
import com.borjaglez.shop.support.web.CurrentUser;
import com.borjaglez.shop.support.web.PageResponse;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.http.spring.FilterableQuery;

/**
 * Orders API. Every order endpoint acts on the orders of the current user; the list accepts the
 * specification-repository HTTP filters on the fields listed below.
 *
 * <p>The list and the detail come from the read model, which Kafka updates a moment after each
 * command, so a new order can take a few hundred milliseconds to show up. The history comes from
 * the event store and is always current.
 */
@RestController
@RequestMapping("/api/orders")
class OrderController {

  private final CommandBus commands;
  private final QueryBus queries;

  OrderController(CommandBus commands, QueryBus queries) {
    this.commands = commands;
    this.queries = queries;
  }

  @GetMapping
  PageResponse<OrderSummary> myOrders(
      @CurrentUser String customer,
      @FilterableQuery(
              value = OrderView.class,
              filterableFields = {"status", "total", "currency", "placedAt", "lines.sku"},
              sortableFields = {"placedAt", "total", "status"})
          QueryPlan<OrderView> plan,
      Pageable pageable) {
    Page<OrderSummary> page = queries.ask(new ListMyOrdersQuery(customer, plan, pageable));
    return PageResponse.of(page);
  }

  @GetMapping("/{id}")
  OrderDetail order(@CurrentUser String customer, @PathVariable UUID id) {
    return queries.ask(new GetOrderQuery(id, customer));
  }

  @GetMapping("/{id}/history")
  List<HistoryEntry> history(@CurrentUser String customer, @PathVariable UUID id) {
    return queries.ask(new GetOrderHistoryQuery(id, customer));
  }

  /** The checkout saga, step by step: stock, payment and, when it failed, what was undone. */
  @GetMapping("/{id}/checkout")
  CheckoutView checkout(@CurrentUser String customer, @PathVariable UUID id) {
    return queries.ask(new GetCheckoutQuery(id, customer));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  CreatedResponse place(@CurrentUser String customer, @Valid @RequestBody PlaceOrderRequest body) {
    UUID id =
        commands.dispatchAndReceive(
            new PlaceOrderCommand(
                customer,
                body.items().stream()
                    .map(i -> new PlaceOrderCommand.Item(i.productId(), i.quantity()))
                    .toList()));
    return new CreatedResponse(id);
  }

  @PostMapping("/{id}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void cancel(
      @CurrentUser String customer,
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) CancelOrderRequest body) {
    commands.dispatchAndWait(
        new CancelOrderCommand(id, customer, body == null ? null : body.reason()));
  }

  /**
   * Event store explorer. It shows every stream, so a real system would keep it for operators; here
   * it is public on purpose, to watch event sourcing and the outbox at work.
   */
  @GetMapping("/events")
  PageResponse<StoredEventView> events(
      @FilterableQuery(
              value = StoredEvent.class,
              filterableFields = {
                "eventType",
                "streamType",
                "streamId",
                "version",
                "occurredAt",
                "publishedAt",
                "publishAttempts"
              },
              sortableFields = {"globalPosition", "occurredAt"})
          QueryPlan<StoredEvent> plan,
      Pageable pageable) {
    Page<StoredEventView> page = queries.ask(new SearchEventStoreQuery(plan, pageable));
    return PageResponse.of(page);
  }
}
