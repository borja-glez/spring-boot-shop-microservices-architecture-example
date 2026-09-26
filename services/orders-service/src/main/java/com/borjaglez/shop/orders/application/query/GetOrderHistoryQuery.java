package com.borjaglez.shop.orders.application.query;

import java.util.Objects;
import java.util.UUID;

import com.borjaglez.cqrs.query.Query;

import lombok.Getter;

/**
 * The event stream of one of the customer's orders, read from the event store rather than the read
 * model. Answered with a {@code List<HistoryEntry>}.
 */
@Getter
public class GetOrderHistoryQuery extends Query {

  private final UUID orderId;
  private final String customerId;

  public GetOrderHistoryQuery(UUID orderId, String customerId) {
    this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
    this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
  }
}
