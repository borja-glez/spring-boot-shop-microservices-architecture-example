package com.borjaglez.shop.eskit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.shop.support.tracing.TraceCarrier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@SpringBootApplication
public class EsKitTestApplication {

  /** Destination that records what the relay publishes and can be told to fail. */
  public static class RecordingDestination implements OutboxDestination {
    public final List<Event> published = new CopyOnWriteArrayList<>();
    public final List<String> correlationIds = new CopyOnWriteArrayList<>();
    public final List<String> contextTraceHeaders = new CopyOnWriteArrayList<>();
    public volatile Consumer<Event> behaviour = event -> {};

    @Override
    public void publish(Event event) {
      behaviour.accept(event);
      published.add(event);
      correlationIds.add(MessageContext.current().correlationId());
      contextTraceHeaders.add(
          String.valueOf(MessageContext.current().asMap().get(TraceCarrier.TRACEPARENT)));
    }

    public void reset() {
      published.clear();
      correlationIds.clear();
      contextTraceHeaders.clear();
      behaviour = event -> {};
    }
  }

  /**
   * Pretends every request runs in the same trace, and records the traces the relay resumes: what
   * the event store captured must come back when the event is published.
   */
  public static class RecordingTraceCarrier implements TraceCarrier {
    public static final String TRACE = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
    public final List<Map<String, String>> resumed = new CopyOnWriteArrayList<>();

    @Override
    public Map<String, String> capture() {
      return Map.of(TRACEPARENT, TRACE);
    }

    @Override
    public <T> T resume(Map<String, String> captured, String name, Supplier<T> work) {
      resumed.add(captured);
      return work.get();
    }
  }

  @Bean
  RecordingTraceCarrier recordingTraceCarrier() {
    return new RecordingTraceCarrier();
  }

  @Bean
  MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean
  RecordingDestination recordingDestination() {
    return new RecordingDestination();
  }
}
