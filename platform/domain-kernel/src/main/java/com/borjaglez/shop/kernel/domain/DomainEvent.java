package com.borjaglez.shop.kernel.domain;

import java.time.Instant;

/** A fact that happened in the domain. */
public interface DomainEvent {

  /** Instant at which the fact happened. */
  Instant occurredOn();
}
