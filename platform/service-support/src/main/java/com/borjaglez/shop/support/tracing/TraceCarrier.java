package com.borjaglez.shop.support.tracing;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Carries a trace across work that runs later, in another thread: an event the outbox relay
 * publishes seconds after the request that recorded it, a checkout step the saga runner takes long
 * after the order was placed. The trace is captured as W3C headers ({@code traceparent}), stored
 * with the work, and resumed when the work runs, so one order is one trace from the HTTP request to
 * the last consumer.
 */
public interface TraceCarrier {

  /** Header of the W3C trace context. */
  String TRACEPARENT = "traceparent";

  /** Header of the vendor-specific W3C trace state. */
  String TRACESTATE = "tracestate";

  /** Carries nothing: for applications without tracing. */
  TraceCarrier NONE =
      new TraceCarrier() {
        @Override
        public Map<String, String> capture() {
          return Map.of();
        }

        @Override
        public <T> T resume(Map<String, String> captured, String name, Supplier<T> work) {
          return work.get();
        }
      };

  /** The trace headers of the current span; empty outside a trace. */
  Map<String, String> capture();

  /**
   * Runs {@code work} in a new span named {@code name} that continues the captured trace, or starts
   * a new trace when nothing was captured.
   */
  <T> T resume(Map<String, String> captured, String name, Supplier<T> work);

  /** Whether a header is part of the trace context rather than application metadata. */
  static boolean isTraceHeader(String name) {
    return TRACEPARENT.equals(name) || TRACESTATE.equals(name);
  }
}
