package com.borjaglez.shop.notifications.api;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.borjaglez.shop.notifications.application.NotificationPublisher;
import com.borjaglez.shop.notifications.application.NotificationQueries.NotificationView;
import com.borjaglez.shop.notifications.domain.Notification;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Keeps the open SSE connections per customer and pushes notices to them. A heartbeat comment every
 * 20 seconds keeps proxies (nginx, the gateway, Kubernetes ingress) from closing idle connections.
 * Connections live in this instance only: with several replicas a notice reaches the tabs connected
 * to the replica that processed the event, and the others see it on their next list request.
 *
 * <p>{@code shop.notifications.sse.connections} reports the open connections.
 */
@Component
public class SseNotificationHub implements NotificationPublisher, MeterBinder {

  private static final Logger log = LoggerFactory.getLogger(SseNotificationHub.class);
  private static final Duration CONNECTION_LIFETIME = Duration.ofMinutes(30);

  private final Map<String, List<SseEmitter>> connections = new ConcurrentHashMap<>();

  /**
   * Pushes run here, not on the Kafka consumer thread: a browser that stops reading would block the
   * consumer until the socket write times out.
   */
  private final ExecutorService pushes = Executors.newVirtualThreadPerTaskExecutor();

  /** Opens a stream for the customer; the browser reconnects when it ends. */
  public SseEmitter connect(String customerId) {
    SseEmitter emitter = new SseEmitter(CONNECTION_LIFETIME.toMillis());
    List<SseEmitter> customerConnections =
        connections.computeIfAbsent(customerId, id -> new CopyOnWriteArrayList<>());
    customerConnections.add(emitter);
    Runnable remove = () -> disconnect(customerId, emitter);
    emitter.onCompletion(remove);
    emitter.onTimeout(remove);
    emitter.onError(error -> remove.run());
    send(emitter, SseEmitter.event().name("ready").data("ok"));
    return emitter;
  }

  @Override
  public void publish(Notification notification) {
    List<SseEmitter> targets = connections.getOrDefault(notification.getCustomerId(), List.of());
    NotificationView view = NotificationView.of(notification);
    for (SseEmitter emitter : targets) {
      pushes.execute(
          () ->
              send(
                  emitter,
                  SseEmitter.event()
                      .id(notification.getEventId())
                      .name("notification")
                      .data(view)));
    }
  }

  /** Forgets a closed connection, and the customer once they have none left. */
  private void disconnect(String customerId, SseEmitter emitter) {
    connections.computeIfPresent(
        customerId,
        (id, list) -> {
          list.remove(emitter);
          return list.isEmpty() ? null : list;
        });
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder("shop.notifications.sse.connections", this, SseNotificationHub::connectionCount)
        .description("Open server-sent event connections")
        .register(registry);
  }

  @PreDestroy
  void shutdown() {
    pushes.shutdown();
  }

  @Scheduled(fixedRate = 20_000)
  public void heartbeat() {
    connections
        .values()
        .forEach(list -> list.forEach(e -> send(e, SseEmitter.event().comment("ping"))));
  }

  int connectionCount() {
    return connections.values().stream().mapToInt(List::size).sum();
  }

  private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
    try {
      emitter.send(event);
    } catch (IOException | IllegalStateException e) {
      // The browser went away; completing the emitter removes it.
      log.debug("Dropping a closed SSE connection: {}", e.toString());
      emitter.completeWithError(e);
    }
  }
}
