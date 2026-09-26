package com.borjaglez.shop.contracts.orders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** A customer placed an order. Prices are frozen at this moment. */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "orders", module = "order", name = "order-placed")
public class OrderPlaced extends Event {

  private UUID orderId;
  private String customerId;
  private List<OrderLine> lines;
  private BigDecimal total;
  private String currency;
}
