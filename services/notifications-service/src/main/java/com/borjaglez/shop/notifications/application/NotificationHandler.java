package com.borjaglez.shop.notifications.application;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.contracts.notifications.GetOrderNotices;
import com.borjaglez.shop.contracts.notifications.OrderNotice;
import com.borjaglez.shop.contracts.notifications.OrderNotices;
import com.borjaglez.shop.notifications.application.NotificationQueries.MarkReadCommand;
import com.borjaglez.shop.notifications.application.NotificationQueries.MyNotificationsQuery;
import com.borjaglez.shop.notifications.application.NotificationQueries.NotificationView;
import com.borjaglez.shop.notifications.application.NotificationQueries.UnreadCountQuery;
import com.borjaglez.shop.notifications.domain.Notification;
import com.borjaglez.shop.notifications.domain.NotificationRepository;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.jpa.SpecificationExecutableQuery;

/**
 * Reads and marks the customer's notices. The queries are built on the server: the service is on
 * Boot 3, where the shared {@code QueryPlans} helper (Boot 4) is not available, so the API takes
 * plain parameters instead of HTTP filter plans. {@link GetOrderNotices} is the only message that
 * arrives from another service, the orders service, over RabbitMQ.
 */
@QueryHandler
@CommandHandler
public class NotificationHandler {

  private final NotificationRepository notifications;
  private final Clock clock;

  public NotificationHandler(NotificationRepository notifications, Clock clock) {
    this.notifications = notifications;
    this.clock = clock;
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public Page<NotificationView> mine(MyNotificationsQuery query) {
    SpecificationExecutableQuery<Notification> notices = ofCustomer(query.getCustomerId());
    if (query.isUnreadOnly()) {
      notices = notices.where("readAt", Operators.IS_NULL, null);
    }
    return notices
        .sort(Sort.by(Sort.Direction.DESC, "occurredAt"))
        .findAll(query.getPageable())
        .map(NotificationView::of);
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public Long unread(UnreadCountQuery query) {
    return ofCustomer(query.getCustomerId()).where("readAt", Operators.IS_NULL, null).count();
  }

  /** The customer only sees their own notices, whatever order id is asked for. */
  @HandleQuery
  @Transactional(readOnly = true)
  public OrderNotices orderNotices(GetOrderNotices query) {
    return new OrderNotices(
        ofCustomer(query.getCustomerId())
            .where("orderId", Operators.EQUALS, query.getOrderId())
            .sort(Sort.by("occurredAt"))
            .findAll()
            .stream()
            .map(
                n ->
                    new OrderNotice(
                        n.getKind().name(),
                        n.getTitle(),
                        n.getBody(),
                        n.getOccurredAt(),
                        n.isRead()))
            .toList());
  }

  @HandleCommand
  @Transactional
  public void markRead(MarkReadCommand command) {
    Notification notification =
        ofCustomer(command.getCustomerId())
            .where("eventId", Operators.EQUALS, command.getNotificationId())
            .findOne()
            .orElseThrow(() -> new NotificationNotFoundException(command.getNotificationId()));
    notification.markRead(OffsetDateTime.now(clock));
    notifications.save(notification);
  }

  private SpecificationExecutableQuery<Notification> ofCustomer(String customerId) {
    return notifications.query().where("customerId", Operators.EQUALS, customerId);
  }
}
