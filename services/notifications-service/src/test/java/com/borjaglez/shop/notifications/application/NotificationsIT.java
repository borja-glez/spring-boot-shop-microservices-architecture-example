package com.borjaglez.shop.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.contracts.orders.OrderCancelled;
import com.borjaglez.shop.contracts.orders.OrderConfirmed;
import com.borjaglez.shop.contracts.orders.OrderPlaced;
import com.borjaglez.shop.contracts.payments.PaymentRefunded;
import com.borjaglez.shop.notifications.TestContainers;
import com.borjaglez.shop.notifications.application.NotificationQueries.MarkReadCommand;
import com.borjaglez.shop.notifications.application.NotificationQueries.MyNotificationsQuery;
import com.borjaglez.shop.notifications.application.NotificationQueries.NotificationView;
import com.borjaglez.shop.notifications.application.NotificationQueries.UnreadCountQuery;
import com.borjaglez.shop.notifications.domain.Notification;
import com.borjaglez.shop.notifications.domain.NotificationKind;
import com.borjaglez.shop.notifications.domain.NotificationRepository;

/** Notices over a real database, fed by calling the projector as the Kafka consumer would. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "shop.notifications.sweep-interval=1h")
@Import(TestContainers.class)
class NotificationsIT {

  @Autowired NotificationProjector projector;
  @Autowired PendingNotificationsSweeper sweeper;
  @Autowired NotificationRepository notifications;
  @Autowired QueryBus queries;
  @Autowired CommandBus commands;

  private static String customer() {
    return "cliente-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private static OrderPlaced placed(UUID orderId, String customer) {
    return new OrderPlaced(orderId, customer, List.of(), new BigDecimal("20.00"), "EUR");
  }

  private Page<NotificationView> mine(String customer, boolean unread) {
    return queries.ask(new MyNotificationsQuery(customer, unread, PageRequest.of(0, 20)));
  }

  @Test
  void aCustomerSeesTheNoticesOfTheirOrdersOnly() {
    String lucia = customer();
    String mateo = customer();
    UUID luciasOrder = UUID.randomUUID();
    UUID mateosOrder = UUID.randomUUID();
    projector.on(placed(luciasOrder, lucia));
    projector.on(placed(mateosOrder, mateo));

    projector.on(new OrderConfirmed(luciasOrder, UUID.randomUUID()));
    projector.on(new OrderCancelled(luciasOrder, "prisa", lucia));
    projector.on(
        new PaymentRefunded(UUID.randomUUID(), luciasOrder, new BigDecimal("20.00"), "EUR"));
    projector.on(new OrderConfirmed(mateosOrder, UUID.randomUUID()));

    assertThat(mine(lucia, false))
        .extracting(NotificationView::kind)
        .containsExactlyInAnyOrder(
            NotificationKind.ORDER_CONFIRMED,
            NotificationKind.ORDER_CANCELLED,
            NotificationKind.PAYMENT_REFUNDED);
    assertThat(mine(mateo, false)).hasSize(1);
  }

  @Test
  void readingANoticeLowersTheUnreadCount() {
    String lucia = customer();
    UUID orderId = UUID.randomUUID();
    projector.on(placed(orderId, lucia));
    projector.on(new OrderConfirmed(orderId, UUID.randomUUID()));
    String id = mine(lucia, false).getContent().getFirst().id();

    assertThat(queries.<Long>ask(new UnreadCountQuery(lucia))).isEqualTo(1);
    commands.dispatchAndWait(new MarkReadCommand(id, lucia));

    assertThat(queries.<Long>ask(new UnreadCountQuery(lucia))).isZero();
    assertThat(mine(lucia, true)).isEmpty();
    assertThatThrownBy(() -> commands.dispatchAndWait(new MarkReadCommand(id, customer())))
        .isInstanceOf(NotificationNotFoundException.class);
  }

  @Test
  void theSweeperAddressesNoticesThatMissedTheirOrder() {
    String lucia = customer();
    UUID orderId = UUID.randomUUID();
    // What a race between two consumer threads leaves behind: a waiting notice and a known owner.
    notifications.save(
        Notification.of(
            UUID.randomUUID().toString(),
            orderId,
            NotificationKind.ORDER_CONFIRMED,
            null,
            null,
            null,
            OffsetDateTime.now()));
    projector.on(placed(orderId, lucia));
    notifications.save(
        Notification.of(
            UUID.randomUUID().toString(),
            orderId,
            NotificationKind.ORDER_CANCELLED,
            null,
            null,
            null,
            OffsetDateTime.now()));

    sweeper.sweep();

    assertThat(mine(lucia, false)).hasSize(2).allSatisfy(n -> assertThat(n.title()).isNotNull());
  }
}
