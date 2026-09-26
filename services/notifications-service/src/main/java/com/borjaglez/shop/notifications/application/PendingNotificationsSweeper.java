package com.borjaglez.shop.notifications.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.shop.notifications.domain.Notification;
import com.borjaglez.shop.notifications.domain.NotificationRepository;
import com.borjaglez.shop.notifications.domain.OrderOwnerRepository;
import com.borjaglez.specrepository.core.Operators;

/**
 * Addresses notices that are still waiting although their order's owner is known.
 *
 * <p>It happens when {@code OrderPlaced} and, say, {@code OrderConfirmed} are processed at the same
 * time on different partitions: each transaction misses the other's insert. Rather than locking,
 * this sweep closes the gap a few seconds later.
 */
@Component
public class PendingNotificationsSweeper {

  private final NotificationRepository notifications;
  private final OrderOwnerRepository owners;
  private final NotificationProjector projector;
  private final Clock clock;

  private static final int BATCH = 500;
  private static final Duration MAX_AGE = Duration.ofDays(1);

  public PendingNotificationsSweeper(
      NotificationRepository notifications,
      OrderOwnerRepository owners,
      NotificationProjector projector,
      Clock clock) {
    this.notifications = notifications;
    this.owners = owners;
    this.projector = projector;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${shop.notifications.sweep-interval:10s}")
  @Transactional
  public void sweep() {
    // Recent notices only, a batch at a time: a notice whose OrderPlaced never arrives (it fell out
    // of Kafka's retention, say) must not be reloaded forever.
    List<UUID> waiting =
        notifications
            .query()
            .where("customerId", Operators.IS_NULL, null)
            .where("occurredAt", Operators.GREATER_THAN, OffsetDateTime.now(clock).minus(MAX_AGE))
            .findSlice(PageRequest.of(0, BATCH))
            .getContent()
            .stream()
            .map(Notification::getOrderId)
            .distinct()
            .toList();
    if (waiting.isEmpty()) {
      return;
    }
    owners
        .query()
        .where("orderId", Operators.IN, waiting)
        .findAll()
        .forEach(projector::addressPending);
  }
}
