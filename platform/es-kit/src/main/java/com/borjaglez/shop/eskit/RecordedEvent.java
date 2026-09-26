package com.borjaglez.shop.eskit;

import java.time.OffsetDateTime;

import com.borjaglez.cqrs.event.Event;

/**
 * An event read back from its stream.
 *
 * @param version position in the stream, from 1
 * @param event the deserialized event
 * @param eventType wire name
 * @param occurredAt when it was stored
 * @param publishedAt when the relay handed it to the broker, {@code null} while pending
 */
public record RecordedEvent(
    long version,
    Event event,
    String eventType,
    OffsetDateTime occurredAt,
    OffsetDateTime publishedAt) {}
