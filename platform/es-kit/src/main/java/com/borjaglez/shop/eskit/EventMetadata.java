package com.borjaglez.shop.eskit;

import java.util.HashMap;
import java.util.Map;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.shop.support.tracing.TraceCarrier;

/** The metadata stored with every event of the event store. */
final class EventMetadata {

  private EventMetadata() {}

  /**
   * The message context of the request plus the current trace, so an event can be followed back to
   * the request that recorded it, and the relay can publish it as part of that trace.
   */
  static Map<String, String> current(TraceCarrier traces) {
    Map<String, String> metadata = new HashMap<>(MessageContext.current().asMap());
    metadata.putAll(traces.capture());
    return metadata;
  }
}
