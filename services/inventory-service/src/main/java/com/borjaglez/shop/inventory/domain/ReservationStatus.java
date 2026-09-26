package com.borjaglez.shop.inventory.domain;

public enum ReservationStatus {
  /** The stock is held for the order. */
  RESERVED,
  /** The stock was given back, or the order was released before anything was reserved. */
  RELEASED
}
