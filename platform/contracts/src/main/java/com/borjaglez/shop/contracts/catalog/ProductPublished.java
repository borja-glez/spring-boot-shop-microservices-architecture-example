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

/** A product became visible in the public catalog and can be bought. */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "catalog", module = "product", name = "product-published")
public class ProductPublished extends Event {

  private UUID productId;
  private String sku;
  private String name;
  private BigDecimal price;
  private String currency;
  private String sellerId;
}
