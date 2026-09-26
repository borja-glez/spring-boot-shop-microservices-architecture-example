package com.borjaglez.shop.contracts.inventory;

import java.util.UUID;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** Someone counted a product and set its units on hand. */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "inventory", module = "stock", name = "stock-adjusted")
public class StockAdjusted extends Event {

  private UUID productId;
  private String sku;
  private int onHand;
  private int previousOnHand;
  private String adjustedBy;
}
