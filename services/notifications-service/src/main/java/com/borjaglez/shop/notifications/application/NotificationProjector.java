package com.borjaglez.shop.notifications.application;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.idempotency.Idempotent;
import com.borjaglez.shop.contracts.orders.OrderCancelled;
import com.borjaglez.shop.contracts.orders.OrderConfirmed;
import com.borjaglez.shop.contracts.orders.OrderPlaced;
import com.borjaglez.shop.contracts.orders.OrderRejected;
import com.borjaglez.shop.contracts.payments.PaymentRefunded;
import com.borjaglez.shop.notifications.domain.Notification;
import com.borjaglez.shop.notifications.domain.NotificationKind;
import com.borjaglez.shop.notifications.domain.NotificationRepository;
import com.borjaglez.shop.notifications.domain.OrderOwner;
import com.borjaglez.shop.notifications.domain.OrderOwnerRepository;
import com.borjaglez.specrepository.core.Operators;

/**
 * Turns order and payment events from Kafka into notices. Every event is applied once ({@link
 * Idempotent}, and the notice is keyed by the event id) and events can arrive in any order across
 * types: a notice whose order is not known yet waits until {@code OrderPlaced} arrives, or until
 * {@link PendingNotificationsSweeper} finds its owner.
 */
@EventHandler
public class NotificationProjector {

  static final String CONSUMER = "notifications.notices";

  private final NotificationRepository notifications;
  private final OrderOwnerRepository owners;
  private final NotificationPublisher publisher;

  public NotificationProjector(
      NotificationRepository notifications,
      OrderOwnerRepository owners,
      NotificationPublisher publisher) {
    this.notifications = notifications;
    this.owners = owners;
    this.publisher = publisher;
  }

  @HandleEvent
  @Idempotent(name = CONSUMER)
  @Transactional
  public void on(OrderPlaced event) {
    OrderOwner owner =
        owner(event.getOrderId())
            .orElseGet(
                () ->
                    owners.save(
                        new OrderOwner(
                            event.getOrderId(),
                            event.getCustomerId(),
                            event.getTotal(),
                            event.getCurrency())));
    addressPending(owner);
  }

  @HandleEvent
  @Idempotent(name = CONSUMER)
  @Transactional
  public void on(OrderConfirmed event) {
    record(event, event.getOrderId(), NotificationKind.ORDER_CONFIRMED, null, null, null);
  }

  @HandleEvent
  @Idempotent(name = CONSUMER)
  @Transactional
  public void on(OrderRejected event) {
    record(
        event, event.getOrderId(), NotificationKind.ORDER_REJECTED, event.getReason(), null, null);
  }

  @HandleEvent
  @Idempotent(name = CONSUMER)
  @Transactional
  public void on(OrderCancelled event) {
    record(
        event, event.getOrderId(), NotificationKind.ORDER_CANCELLED, event.getReason(), null, null);
  }

  @HandleEvent
  @Idempotent(name = CONSUMER)
  @Transactional
  public void on(PaymentRefunded event) {
    record(
        event,
        event.getOrderId(),
        NotificationKind.PAYMENT_REFUNDED,
        null,
        event.getAmount(),
        event.getCurrency());
  }

  /** Addresses the notices of an order that were waiting for its owner. */
  public void addressPending(OrderOwner owner) {
    List<Notification> pending =
        notifications
            .query()
            .where("orderId", Operators.EQUALS, owner.getOrderId())
            .where("customerId", Operators.IS_NULL, null)
            .findAll();
    for (Notification notification : pending) {
      notification.addressTo(owner);
      notifications.save(notification);
      afterCommit(notification);
    }
  }

  private void record(
      Event event,
      UUID orderId,
      NotificationKind kind,
      String detail,
      BigDecimal amount,
      String currency) {
    Notification notification =
        Notification.of(
            event.getEventId(),
            orderId,
            kind,
            detail,
            amount,
            currency,
            event.getOccurredOn().atOffset(ZoneOffset.UTC));
    owner(orderId).ifPresent(notification::addressTo);
    notifications.save(notification);
    if (notification.isAddressed()) {
      afterCommit(notification);
    }
  }

  private Optional<OrderOwner> owner(UUID orderId) {
    return owners.query().where("orderId", Operators.EQUALS, orderId).findOne();
  }

  /** The browser hears about a notice only once it is stored. */
  private void afterCommit(Notification notification) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      publisher.publish(notification);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            publisher.publish(notification);
          }
        });
  }
}
