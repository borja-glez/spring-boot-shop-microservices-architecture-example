package com.borjaglez.shop.orders.application.query;

import java.util.Objects;
import java.util.UUID;

import com.borjaglez.cqrs.query.Query;

import lombok.Getter;

/**
 * The notices the customer received about one of their orders. Answered with an {@code
 * OrderNoticesView}.
 */
@Getter
public class GetOrderNoticesQuery extends Query {

  private final UUID orderId;
  private final String customerId;

  public GetOrderNoticesQuery(UUID orderId, String customerId) {
    this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
    this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
  }
}
