package com.borjaglez.shop.reporting.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A product line of an order. Product reports group lines and filter them by their order. */
@Entity
@Table(name = "report_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportLine {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id")
  private ReportOrder order;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(nullable = false, length = 40)
  private String sku;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal revenue;

  public ReportLine(
      ReportOrder order,
      UUID productId,
      String sku,
      String name,
      int quantity,
      BigDecimal unitPrice) {
    this.id = UUID.randomUUID();
    this.order = order;
    this.productId = productId;
    this.sku = sku;
    this.name = name;
    this.quantity = quantity;
    this.revenue = unitPrice.multiply(BigDecimal.valueOf(quantity));
  }
}
