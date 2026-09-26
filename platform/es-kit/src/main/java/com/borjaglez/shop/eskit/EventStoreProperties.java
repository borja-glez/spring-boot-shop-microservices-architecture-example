package com.borjaglez.shop.eskit;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the event store and its relay.
 *
 * @param eventStore where to find the event classes that can be stored
 * @param outbox relay settings
 */
@ConfigurationProperties("shop")
public record EventStoreProperties(
    @DefaultValue EventStoreSettings eventStore, @DefaultValue OutboxSettings outbox) {

  /** Packages scanned for {@code @CqrsMessage} events. */
  public record EventStoreSettings(
      @DefaultValue("com.borjaglez.shop.contracts") List<String> eventPackages) {}

  /** Outbox relay. */
  public record OutboxSettings(@DefaultValue RelaySettings relay) {}

  /**
   * @param enabled whether the scheduled relay runs
   * @param interval pause between runs
   * @param batchSize rows locked and published per transaction
   */
  public record RelaySettings(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("500ms") Duration interval,
      @DefaultValue("100") int batchSize) {}
}
