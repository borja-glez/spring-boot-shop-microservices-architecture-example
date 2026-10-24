package com.borjaglez.shop.eskit;

import com.borjaglez.shop.support.chaos.Chaos;
import com.borjaglez.shop.support.chaos.ChaosFault;

/**
 * The chaos faults of the relays that publish integration events: {@link OutboxRelay}, which relays
 * the event store, and {@link ChaosEventBus}, through which the relay of the spring-boot-cqrs
 * outbox publishes. Both register the same faults, so the demo turns either relay off or on with
 * one switch.
 */
final class RelayFaults {

  static final String PAUSED = "relay.paused";
  static final String PAUSED_DESCRIPTION =
      "Stops publishing events to Kafka, as if the broker were down";
  static final String DUPLICATE = "relay.duplicate";
  static final String DUPLICATE_DESCRIPTION =
      "Publishes every event twice (at-least-once delivery)";

  private RelayFaults() {}

  static ChaosFault paused(Chaos chaos) {
    return chaos.toggle(PAUSED, PAUSED_DESCRIPTION);
  }

  static ChaosFault duplicate(Chaos chaos) {
    return chaos.toggle(DUPLICATE, DUPLICATE_DESCRIPTION);
  }
}
