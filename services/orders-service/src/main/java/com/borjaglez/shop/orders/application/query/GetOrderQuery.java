package com.borjaglez.shop.orders.application.query;

import java.util.Objects;
import java.util.UUID;

import com.borjaglez.cqrs.query.Query;

import lombok.Getter;

/** One of the customer's orders, from the read model. Answered with an {@code OrderDetail}. */
@Getter
public class GetOrderQuery extends Query {

  private final UUID orderId;
  private final String customerId;

  public GetOrderQuery(UUID orderId, String customerId) {
    this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
    this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
  }
}
