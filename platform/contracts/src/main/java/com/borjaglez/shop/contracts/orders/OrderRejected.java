package com.borjaglez.shop.contracts.orders;

import java.util.UUID;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * The checkout failed and everything it did was undone.
 *
 * <p>{@code reason} is a stable code ({@code out-of-stock}, {@code card-limit-exceeded}, {@code
 * inventory-unavailable}, {@code payment-unavailable}); {@code detail} explains it.
 */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "orders", module = "order", name = "order-rejected")
public class OrderRejected extends Event {

  private UUID orderId;
  private String reason;
  private String detail;
}
