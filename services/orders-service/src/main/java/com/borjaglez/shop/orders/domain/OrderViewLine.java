package com.borjaglez.shop.orders.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A line of an order as the read model shows it. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class OrderViewLine {

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(nullable = false, length = 40)
  private String sku;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  public BigDecimal subtotal() {
    return unitPrice.multiply(BigDecimal.valueOf(quantity));
  }
}
