package com.borjaglez.shop.contracts.orders;

import java.util.UUID;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** An order was cancelled before it was fulfilled. */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "orders", module = "order", name = "order-cancelled")
public class OrderCancelled extends Event {

  private UUID orderId;
  private String reason;
  private String cancelledBy;
}
