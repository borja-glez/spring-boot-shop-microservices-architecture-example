package com.borjaglez.shop.notifications.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.notifications.domain.Notification;
import com.borjaglez.shop.notifications.domain.NotificationKind;
import com.borjaglez.shop.notifications.domain.OrderOwner;

class SseNotificationHubTest {

  private static Notification addressed(String customer) {
    UUID orderId = UUID.randomUUID();
    Notification notification =
        Notification.of(
            "e-1",
            orderId,
            NotificationKind.ORDER_CONFIRMED,
            null,
            null,
            null,
            OffsetDateTime.parse("2026-09-26T10:00:00Z"));
    notification.addressTo(new OrderOwner(orderId, customer, BigDecimal.TEN, "EUR"));
    return notification;
  }

  @Test
  void connectionsArePerCustomerAndGoAwayWhenClosed() {
    SseNotificationHub hub = new SseNotificationHub();

    var lucia = hub.connect("cliente-lucia");
    hub.connect("cliente-lucia");
    hub.connect("cliente-mateo");
    assertThat(hub.connectionCount()).isEqualTo(3);

    // Outside a request the emitters cannot write, so publishing drops them, as for a closed tab.
    hub.publish(addressed("cliente-lucia"));
    hub.heartbeat();
    lucia.complete();

    assertThat(hub.connectionCount()).isLessThanOrEqualTo(3);
  }

  @Test
  void aNoticeForSomeoneWithoutConnectionsIsFine() {
    SseNotificationHub hub = new SseNotificationHub();

    hub.publish(addressed("cliente-nadie"));

    assertThat(hub.connectionCount()).isZero();
  }
}
