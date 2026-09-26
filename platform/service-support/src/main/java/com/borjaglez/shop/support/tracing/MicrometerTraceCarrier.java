package com.borjaglez.shop.support.tracing;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

/** {@link TraceCarrier} over Micrometer Tracing and its configured propagation (W3C by default). */
public class MicrometerTraceCarrier implements TraceCarrier {

  private final Tracer tracer;
  private final Propagator propagator;

  public MicrometerTraceCarrier(Tracer tracer, Propagator propagator) {
    this.tracer = tracer;
    this.propagator = propagator;
  }

  @Override
  public Map<String, String> capture() {
    TraceContext context = tracer.currentTraceContext().context();
    if (context == null) {
      return Map.of();
    }
    Map<String, String> headers = new HashMap<>();
    propagator.inject(context, headers, Map::put);
    return Map.copyOf(headers);
  }

  @Override
  public <T> T resume(Map<String, String> captured, String name, Supplier<T> work) {
    Span.Builder builder =
        captured.containsKey(TRACEPARENT)
            ? propagator.extract(captured, Map::get)
            : tracer.spanBuilder().setNoParent();
    Span span = builder.name(name).start();
    try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
      return work.get();
    } catch (RuntimeException | Error e) {
      span.error(e);
      throw e;
    } finally {
      span.end();
    }
  }
}
