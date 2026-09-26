package com.borjaglez.shop.contracts.catalog;

import java.util.UUID;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** A product was withdrawn from sale for good. */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "catalog", module = "product", name = "product-discontinued")
public class ProductDiscontinued extends Event {

  private UUID productId;
  private String reason;
}
