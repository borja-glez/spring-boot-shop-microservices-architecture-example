package com.borjaglez.shop.support.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

/** With the real OpenTelemetry bridge: what is captured in one thread is resumed in another. */
class MicrometerTraceCarrierTest {

  private final OpenTelemetrySdk sdk =
      OpenTelemetrySdk.builder()
          .setTracerProvider(SdkTracerProvider.builder().build())
          .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
          .build();
  private final Tracer tracer =
      new OtelTracer(sdk.getTracer("test"), new OtelCurrentTraceContext(), event -> {});
  private final TraceCarrier carrier =
      new MicrometerTraceCarrier(
          tracer, new OtelPropagator(sdk.getPropagators(), sdk.getTracer("test")));

  @Test
  void outsideATraceThereIsNothingToCapture() {
    assertThat(carrier.capture()).isEmpty();
  }

  @Test
  void workResumedLaterBelongsToTheCapturedTrace() throws Exception {
    Span request = tracer.nextSpan().name("request").start();
    Map<String, String> captured;
    try (Tracer.SpanInScope scope = tracer.withSpan(request)) {
      captured = carrier.capture();
    } finally {
      request.end();
    }
    assertThat(captured).containsKey(TraceCarrier.TRACEPARENT);

    String[] seen = new String[2];
    Thread later =
        Thread.ofVirtual()
            .start(
                () ->
                    carrier.resume(
                        captured,
                        "outbox publish",
                        () -> {
                          seen[0] = tracer.currentSpan().context().traceId();
                          seen[1] = tracer.currentSpan().context().parentId();
                          return null;
                        }));
    later.join();

    assertThat(seen[0]).isEqualTo(request.context().traceId());
    assertThat(seen[1]).isEqualTo(request.context().spanId());
    assertThat(tracer.currentSpan()).isNull();
  }

  @Test
  void withoutACapturedTraceTheWorkStartsItsOwn() {
    String traceId =
        carrier.resume(Map.of(), "checkout", () -> tracer.currentSpan().context().traceId());

    assertThat(traceId).isNotBlank();
  }

  @Test
  void aFailureEndsTheSpanAndPropagates() {
    assertThatThrownBy(
            () ->
                carrier.resume(
                    Map.of(),
                    "checkout",
                    () -> {
                      throw new IllegalStateException("payments down");
                    }))
        .hasMessage("payments down");
    assertThat(tracer.currentSpan()).isNull();
  }

  @Test
  void anErrorAlsoEndsTheSpanAndPropagates() {
    assertThatThrownBy(
            () ->
                carrier.resume(
                    Map.of(),
                    "checkout",
                    () -> {
                      throw new LinkageError("missing hint");
                    }))
        .isInstanceOf(LinkageError.class);
    assertThat(tracer.currentSpan()).isNull();
  }

  @Test
  void theNoOpCarrierJustRunsTheWork() {
    assertThat(TraceCarrier.NONE.capture()).isEmpty();
    assertThat(TraceCarrier.NONE.resume(Map.of(), "x", () -> 42)).isEqualTo(42);
    assertThat(TraceCarrier.isTraceHeader("traceparent")).isTrue();
    assertThat(TraceCarrier.isTraceHeader("tracestate")).isTrue();
    assertThat(TraceCarrier.isTraceHeader("correlationId")).isFalse();
  }
}
