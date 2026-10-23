package com.borjaglez.shop.eskit;

import java.time.OffsetDateTime;

import com.borjaglez.shop.kernel.domain.DomainEvent;

/**
 * A domain event read back from its stream.
 *
 * @param version position in the stream, from 1
 * @param event the deserialized event
 * @param eventType name the event is stored under ({@link DomainEventTypes})
 * @param occurredAt when it was stored
 */
public record StoredDomainEvent(
    long version, DomainEvent event, String eventType, OffsetDateTime occurredAt) {}
