package com.borjaglez.shop.orders.domain;

/** Steps of the checkout saga, in the order they can run. */
public enum CheckoutStep {
  /** Ask the inventory to hold the order's products. */
  RESERVE_STOCK,
  /** Ask payments to authorize the order's total. */
  AUTHORIZE_PAYMENT,
  /** Record that the checkout succeeded. */
  CONFIRM_ORDER,
  /** Compensation: refund or void the payment. */
  REFUND_PAYMENT,
  /** Compensation: give the stock back. */
  RELEASE_STOCK,
  /** Record that the checkout failed. */
  REJECT_ORDER,
  /** Nothing left to do. */
  DONE;

  /** Steps that ask another service, as opposed to steps that write the order. */
  public boolean isRemote() {
    return this == RESERVE_STOCK
        || this == AUTHORIZE_PAYMENT
        || this == REFUND_PAYMENT
        || this == RELEASE_STOCK;
  }
}
