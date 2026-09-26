package com.borjaglez.shop.orders.domain;

/**
 * Lifecycle of an order. The checkout saga takes a {@code PLACED} order to {@code CONFIRMED} or
 * {@code REJECTED}; only a confirmed order can be cancelled.
 */
public enum OrderStatus {
  /** Placed; the checkout (stock and payment) is running. */
  PLACED(0),
  /** Stock reserved and payment authorized. */
  CONFIRMED(1),
  /** The checkout failed and was undone. */
  REJECTED(1),
  /** Cancelled by the customer after confirmation; payment refunded and stock released. */
  CANCELLED(2);

  private final int rank;

  OrderStatus(int rank) {
    this.rank = rank;
  }

  /** Whether an order in this status can move to {@code next}. Statuses never go back. */
  public boolean isBefore(OrderStatus next) {
    return rank < next.rank;
  }
}
