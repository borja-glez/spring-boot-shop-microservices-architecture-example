package com.borjaglez.shop.eskit;

import java.util.List;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.shop.kernel.domain.DomainEvent;

/**
 * Translates a domain event into the integration events other services receive (the published
 * language of {@code platform/contracts}). {@link DomainEventStore} stores the domain event and
 * hands its translations to the outbox in the same transaction. A domain event that concerns no
 * other service translates into an empty list.
 */
@FunctionalInterface
public interface IntegrationEventTranslator {

  List<Event> translate(DomainEvent event);
}
