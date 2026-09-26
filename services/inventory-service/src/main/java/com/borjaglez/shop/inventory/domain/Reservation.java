package com.borjaglez.shop.inventory.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Stock held for one order. It is never deleted: once released it remains as a tombstone, so a
 * reserve command that arrives late (a retry after a timeout) cannot hold the stock again.
 */
@Entity
@Table(name = "reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

  @Id
  @Column(name = "order_id")
  private UUID orderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReservationStatus status;

  @Column(name = "reserved_at")
  private OffsetDateTime reservedAt;

  @Column(name = "released_at")
  private OffsetDateTime releasedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @ElementCollection
  @CollectionTable(name = "reservation_line", joinColumns = @JoinColumn(name = "order_id"))
  @OrderColumn(name = "line_no")
  private List<ReservedLine> lines = new ArrayList<>();

  public static Reservation reserved(UUID orderId, List<ReservedLine> lines, OffsetDateTime at) {
    Reservation reservation = new Reservation();
    reservation.orderId = orderId;
    reservation.status = ReservationStatus.RESERVED;
    reservation.lines.addAll(lines);
    reservation.reservedAt = at;
    return reservation;
  }

  /** An order released before any stock was reserved for it. */
  public static Reservation releasedBeforeReserving(UUID orderId, OffsetDateTime at) {
    Reservation reservation = new Reservation();
    reservation.orderId = orderId;
    reservation.status = ReservationStatus.RELEASED;
    reservation.releasedAt = at;
    return reservation;
  }

  public boolean isReserved() {
    return status == ReservationStatus.RESERVED;
  }

  public void release(OffsetDateTime at) {
    if (!isReserved()) {
      throw new IllegalStateException("Reservation of " + orderId + " is already released");
    }
    status = ReservationStatus.RELEASED;
    releasedAt = at;
  }
}
