package com.borjaglez.shop.eskit.fixtures;

import java.time.Instant;

import com.borjaglez.shop.kernel.domain.DomainEvent;

/** Domain events of the {@link Tally}. */
public sealed interface TallyEvent extends DomainEvent {

  String tallyId();

  record Opened(String tallyId, Instant occurredOn) implements TallyEvent {}

  record Incremented(String tallyId, int amount, Instant occurredOn) implements TallyEvent {}
}
