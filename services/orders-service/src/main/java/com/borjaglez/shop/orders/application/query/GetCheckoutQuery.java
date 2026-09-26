package com.borjaglez.shop.orders.application.query;

import java.util.Objects;
import java.util.UUID;

import com.borjaglez.cqrs.query.Query;

import lombok.Getter;

/** The checkout saga of one of the customer's orders. Answered with a {@code CheckoutView}. */
@Getter
public class GetCheckoutQuery extends Query {

  private final UUID orderId;
  private final String customerId;

  public GetCheckoutQuery(UUID orderId, String customerId) {
    this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
    this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
  }
}
