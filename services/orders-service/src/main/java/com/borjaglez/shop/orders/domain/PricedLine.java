package com.borjaglez.shop.orders.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** A requested product with the price the catalog currently asks for it. */
public record PricedLine(
    UUID productId, String sku, String name, int quantity, BigDecimal unitPrice, String currency) {

  BigDecimal subtotal() {
    return unitPrice.multiply(BigDecimal.valueOf(quantity));
  }
}
