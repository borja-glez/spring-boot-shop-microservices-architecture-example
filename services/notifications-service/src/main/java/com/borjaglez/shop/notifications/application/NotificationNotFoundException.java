package com.borjaglez.shop.notifications.application;

/** The customer has no notice with that id. */
public class NotificationNotFoundException extends RuntimeException {

  public NotificationNotFoundException(String id) {
    super("Unknown notification " + id);
  }
}
