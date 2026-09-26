package com.borjaglez.shop.eskit;

import com.borjaglez.cqrs.event.Event;

/**
 * Where the relay sends stored events, typically a remote event bus such as the Kafka one. It must
 * return only once the broker has accepted the event, and throw otherwise.
 */
@FunctionalInterface
public interface OutboxDestination {

  void publish(Event event);
}
