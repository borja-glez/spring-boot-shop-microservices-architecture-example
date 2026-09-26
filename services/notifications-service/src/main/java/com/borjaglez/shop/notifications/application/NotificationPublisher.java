package com.borjaglez.shop.notifications.application;

import com.borjaglez.shop.notifications.domain.Notification;

/** Pushes a notice to the customer's open browser tabs, if any. */
public interface NotificationPublisher {

  void publish(Notification notification);
}
