package com.borjaglez.shop.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NotificationTest {

  private static final UUID ORDER = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
  private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-09-26T10:00:00Z");
  private static final OrderOwner LUCIA =
      new OrderOwner(ORDER, "cliente-lucia", new BigDecimal("37.80"), "EUR");

  private static Notification notice(NotificationKind kind, String detail) {
    return Notification.of("e-1", ORDER, kind, detail, null, null, T0);
  }

  @Test
  void aNoticeWaitsUntilItsOwnerIsKnown() {
    Notification notice = notice(NotificationKind.ORDER_CONFIRMED, null);

    assertThat(notice.isAddressed()).isFalse();
    assertThat(notice.getTitle()).isNull();

    notice.addressTo(LUCIA);

    assertThat(notice.isAddressed()).isTrue();
    assertThat(notice.getCustomerId()).isEqualTo("cliente-lucia");
    assertThat(notice.getTitle()).isEqualTo("Order confirmed");
    assertThat(notice.getBody()).contains("€37.80");
  }

  @Test
  void eachKindIsWorded() {
    Notification rejected = notice(NotificationKind.ORDER_REJECTED, "card-limit-exceeded");
    Notification unknownReason = notice(NotificationKind.ORDER_REJECTED, "something-new");
    Notification cancelled = notice(NotificationKind.ORDER_CANCELLED, "in a hurry");
    Notification refunded =
        Notification.of(
            "e-2",
            ORDER,
            NotificationKind.PAYMENT_REFUNDED,
            null,
            new BigDecimal("12.5"),
            "EUR",
            T0);

    rejected.addressTo(LUCIA);
    unknownReason.addressTo(LUCIA);
    cancelled.addressTo(LUCIA);
    refunded.addressTo(LUCIA);

    assertThat(rejected.getBody())
        .contains("exceeds your card limit")
        .contains("You have not been charged");
    assertThat(unknownReason.getBody()).contains("something-new");
    assertThat(cancelled.getTitle()).isEqualTo("Order cancelled");
    assertThat(refunded.getBody()).contains("€12.50");
  }

  @Test
  void aRejectionWithoutReasonOrVoidedIsStillWorded() {
    Notification noReason = notice(NotificationKind.ORDER_REJECTED, null);
    Notification voided = notice(NotificationKind.ORDER_REJECTED, "voided");

    noReason.addressTo(LUCIA);
    voided.addressTo(LUCIA);

    assertThat(noReason.getBody()).contains("checkout could not be completed");
    assertThat(voided.getBody()).contains("was voided");
  }

  @Test
  void aNoticeIsAddressedOnlyToTheOwnerOfItsOrder() {
    Notification notice = notice(NotificationKind.ORDER_CONFIRMED, null);
    OrderOwner other = new OrderOwner(UUID.randomUUID(), "cliente-mateo", BigDecimal.ONE, "EUR");

    assertThatThrownBy(() -> notice.addressTo(other)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void readingTwiceKeepsTheFirstTime() {
    Notification notice = notice(NotificationKind.ORDER_CONFIRMED, null);

    notice.markRead(T0);
    notice.markRead(T0.plusHours(1));

    assertThat(notice.isRead()).isTrue();
    assertThat(notice.getReadAt()).isEqualTo(T0);
  }
}
