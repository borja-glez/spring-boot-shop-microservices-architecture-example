package com.borjaglez.shop.contracts.inventory;

import java.util.List;
import java.util.UUID;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** Stock was reserved for an order. */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "inventory", module = "stock", name = "stock-reserved")
public class StockReserved extends Event {

  private UUID orderId;
  private List<ReservationLine> lines;
}
