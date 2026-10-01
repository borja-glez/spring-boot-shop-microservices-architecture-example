package com.borjaglez.shop.orders.application.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.borjaglez.shop.contracts.notifications.OrderNotice;

/** The notices the notifications service sent about an order. */
public interface OrderNoticesGateway {

  /**
   * Asks the notifications service for the notices of one of the customer's orders.
   *
   * @return the notices, oldest first; empty when the notifications service did not answer
   */
  Optional<List<OrderNotice>> notices(UUID orderId, String customerId);
}
