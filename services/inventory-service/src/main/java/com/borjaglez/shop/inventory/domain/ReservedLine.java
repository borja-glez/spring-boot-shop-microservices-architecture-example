package com.borjaglez.shop.inventory.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Units of one product held by a reservation. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ReservedLine {

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(nullable = false, length = 40)
  private String sku;

  @Column(nullable = false)
  private int quantity;
}
