package com.borjaglez.shop.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.support.error.BusinessRuleViolationException;

class StockItemTest {

  private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-09-25T10:00:00Z");
  private static final OffsetDateTime T1 = T0.plusMinutes(5);

  private static StockItem coffee(int onHand) {
    return StockItem.stocked(UUID.randomUUID(), "CAF-001", "Café", onHand, T0);
  }

  @Test
  void reservedUnitsAreNoLongerAvailable() {
    StockItem item = coffee(10);

    item.reserve(4, T1);

    assertThat(item.getReserved()).isEqualTo(4);
    assertThat(item.available()).isEqualTo(6);
    assertThat(item.getUpdatedAt()).isEqualTo(T1);
  }

  @Test
  void releasedUnitsAreAvailableAgain() {
    StockItem item = coffee(10);
    item.reserve(4, T0);

    item.release(3, T1);

    assertThat(item.available()).isEqualTo(9);
  }

  @Test
  void itCannotHoldOrGiveBackMoreThanItHas() {
    StockItem item = coffee(2);

    assertThatThrownBy(() -> item.reserve(3, T1)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> item.reserve(0, T1)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> item.release(1, T1)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> item.release(0, T1)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> coffee(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void countingSetsTheUnitsOnHand() {
    StockItem item = coffee(10);
    item.reserve(4, T0);

    int previous = item.adjust(4, T1);

    assertThat(previous).isEqualTo(10);
    assertThat(item.getOnHand()).isEqualTo(4);
    assertThat(item.available()).isZero();
  }

  @Test
  void reservedUnitsCannotDisappear() {
    StockItem item = coffee(10);
    item.reserve(4, T0);

    assertThatThrownBy(() -> item.adjust(3, T1))
        .isInstanceOf(BusinessRuleViolationException.class)
        .extracting("code")
        .isEqualTo("below-reserved");
    assertThatThrownBy(() -> item.adjust(-1, T1))
        .isInstanceOf(BusinessRuleViolationException.class)
        .extracting("code")
        .isEqualTo("invalid-stock");
  }

  @Test
  void aReservationIsReleasedOnce() {
    Reservation reservation =
        Reservation.reserved(
            UUID.randomUUID(), List.of(new ReservedLine(UUID.randomUUID(), "CAF-001", 2)), T0);

    reservation.release(T1);

    assertThat(reservation.isReserved()).isFalse();
    assertThat(reservation.getReleasedAt()).isEqualTo(T1);
    assertThatThrownBy(() -> reservation.release(T1)).isInstanceOf(IllegalStateException.class);
    assertThat(Reservation.releasedBeforeReserving(UUID.randomUUID(), T1).isReserved()).isFalse();
  }
}
