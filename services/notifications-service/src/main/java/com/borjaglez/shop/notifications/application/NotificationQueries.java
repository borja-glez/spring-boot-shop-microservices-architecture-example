package com.borjaglez.shop.notifications.application;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.shop.notifications.domain.Notification;
import com.borjaglez.shop.notifications.domain.NotificationKind;

import lombok.Getter;

/** Messages and views of the notices API. */
public final class NotificationQueries {

  private NotificationQueries() {}

  /** The customer's notices, newest first. Answered with a {@code Page<NotificationView>}. */
  @Getter
  public static class MyNotificationsQuery extends Query {

    private final String customerId;
    private final boolean unreadOnly;
    private final Pageable pageable;

    public MyNotificationsQuery(String customerId, boolean unreadOnly, Pageable pageable) {
      this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
      this.unreadOnly = unreadOnly;
      this.pageable = Objects.requireNonNull(pageable, "pageable must not be null");
    }
  }

  /** How many notices the customer has not read. Answered with a {@code Long}. */
  @Getter
  public static class UnreadCountQuery extends Query {

    private final String customerId;

    public UnreadCountQuery(String customerId) {
      this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
    }
  }

  /** The customer read a notice. */
  @Getter
  public static class MarkReadCommand extends Command {

    private final String notificationId;
    private final String customerId;

    public MarkReadCommand(String notificationId, String customerId) {
      this.notificationId = Objects.requireNonNull(notificationId, "id must not be null");
      this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
    }
  }

  /** A notice as the browser shows it. */
  public record NotificationView(
      String id,
      UUID orderId,
      NotificationKind kind,
      String title,
      String body,
      OffsetDateTime occurredAt,
      boolean read) {

    public static NotificationView of(Notification notification) {
      return new NotificationView(
          notification.getEventId(),
          notification.getOrderId(),
          notification.getKind(),
          notification.getTitle(),
          notification.getBody(),
          notification.getOccurredAt(),
          notification.isRead());
    }
  }
}
