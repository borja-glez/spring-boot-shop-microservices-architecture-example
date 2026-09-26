package com.borjaglez.shop.notifications.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Who placed an order and what it cost, so notices can be addressed and worded. */
@Entity
@Table(name = "order_owner")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderOwner {

  @Id
  @Column(name = "order_id")
  private UUID orderId;

  @Column(name = "customer_id", nullable = false, length = 64)
  private String customerId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal total;

  @Column(nullable = false, length = 3)
  private String currency;

  public OrderOwner(UUID orderId, String customerId, BigDecimal total, String currency) {
    this.orderId = orderId;
    this.customerId = customerId;
    this.total = total;
    this.currency = currency;
  }
}
