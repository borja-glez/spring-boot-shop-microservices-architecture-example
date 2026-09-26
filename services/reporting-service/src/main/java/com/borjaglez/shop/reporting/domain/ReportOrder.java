package com.borjaglez.shop.reporting.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An order as reporting counts it. Created by whichever of its events arrives first; the status
 * only moves forward, so a late {@code OrderPlaced} fills in the amounts without undoing a
 * confirmation or a cancellation. Events are keyed by message type on the shared topic, so ordering
 * is guaranteed only within one event type.
 */
@Entity
@Table(name = "report_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportOrder {

  @Id
  @Column(name = "order_id")
  private UUID orderId;

  @Column(name = "customer_id", length = 64)
  private String customerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReportStatus status;

  @Column(precision = 12, scale = 2)
  private BigDecimal total;

  @Column(length = 3)
  private String currency;

  @Column(name = "line_count", nullable = false)
  private int lineCount;

  @Column(name = "placed_at")
  private OffsetDateTime placedAt;

  /** The UTC day it was placed; reports group by it. */
  @Column(name = "placed_day")
  private LocalDate placedDay;

  @Column(name = "decided_at")
  private OffsetDateTime decidedAt;

  @Column(name = "rejection_reason", length = 40)
  private String rejectionReason;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  public static ReportOrder unknown(UUID orderId) {
    ReportOrder order = new ReportOrder();
    order.orderId = orderId;
    order.status = ReportStatus.PLACED;
    return order;
  }

  public void placed(
      String customerId, BigDecimal total, String currency, int lines, OffsetDateTime at) {
    this.customerId = customerId;
    this.total = total;
    this.currency = currency;
    this.lineCount = lines;
    this.placedAt = at;
    this.placedDay = at.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
  }

  public boolean isPlaced() {
    return placedAt != null;
  }

  public void confirmed(OffsetDateTime at) {
    decide(ReportStatus.CONFIRMED, at);
  }

  public void rejected(String reason, OffsetDateTime at) {
    rejectionReason = reason;
    decide(ReportStatus.REJECTED, at);
  }

  public void cancelled(OffsetDateTime at) {
    if (status.isBefore(ReportStatus.CANCELLED)) {
      status = ReportStatus.CANCELLED;
    }
  }

  private void decide(ReportStatus outcome, OffsetDateTime at) {
    if (decidedAt == null) {
      decidedAt = at;
    }
    if (status.isBefore(outcome)) {
      status = outcome;
    }
  }
}
