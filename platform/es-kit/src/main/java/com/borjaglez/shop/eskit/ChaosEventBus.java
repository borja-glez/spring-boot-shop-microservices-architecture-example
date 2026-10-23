package com.borjaglez.shop.eskit;

import java.util.List;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.shop.support.chaos.Chaos;
import com.borjaglez.shop.support.chaos.ChaosException;
import com.borjaglez.shop.support.chaos.ChaosFault;

/**
 * The event bus the relay of the spring-boot-cqrs outbox publishes through ({@code
 * cqrs.outbox.relay.event-bus=chaosEventBus}): the Kafka event bus, with the relay's chaos faults.
 *
 * <ul>
 *   <li>{@code relay.paused} fails every publication with a technical error, as if Kafka were down.
 *       The relay stops its batch at the failed event and retries it on its next run, so events
 *       pile up in the outbox and go out in order once the fault is turned off.
 *   <li>{@code relay.duplicate} publishes every event twice (at-least-once delivery), which the
 *       idempotent consumers must absorb.
 * </ul>
 */
public class ChaosEventBus implements EventBus {

  private final EventBus delegate;
  private final ChaosFault paused;
  private final ChaosFault duplicate;

  public ChaosEventBus(EventBus delegate, Chaos chaos) {
    this.delegate = delegate;
    this.paused =
        chaos.toggle(
            "relay.paused", "Stops publishing events to Kafka, as if the broker were down");
    this.duplicate =
        chaos.toggle("relay.duplicate", "Publishes every event twice (at-least-once delivery)");
  }

  @Override
  public void publish(Event event) {
    if (paused.active()) {
      throw new ChaosException("Chaos: the relay is paused, as if the broker were down");
    }
    delegate.publish(event);
    if (duplicate.active()) {
      delegate.publish(event);
    }
  }

  @Override
  public void publish(List<Event> events) {
    events.forEach(this::publish);
  }
}
