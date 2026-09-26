package com.borjaglez.shop.contracts.catalog;

import java.math.BigDecimal;
import java.util.UUID;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** The selling price of a published product changed. */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "catalog", module = "product", name = "product-price-changed")
public class ProductPriceChanged extends Event {

  private UUID productId;
  private BigDecimal oldPrice;
  private BigDecimal newPrice;
  private String currency;
}
