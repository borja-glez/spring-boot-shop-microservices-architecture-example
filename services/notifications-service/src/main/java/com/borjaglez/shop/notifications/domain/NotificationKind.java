package com.borjaglez.shop.notifications.domain;

/** What a notice tells the customer. */
public enum NotificationKind {
  ORDER_CONFIRMED,
  ORDER_REJECTED,
  ORDER_CANCELLED,
  PAYMENT_REFUNDED
}
