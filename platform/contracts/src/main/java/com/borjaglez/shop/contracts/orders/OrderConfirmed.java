package com.borjaglez.shop.contracts.orders;

import java.util.UUID;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** The checkout succeeded: stock is reserved and the payment authorized. */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "orders", module = "order", name = "order-confirmed")
public class OrderConfirmed extends Event {

  private UUID orderId;
  private UUID paymentId;
}
