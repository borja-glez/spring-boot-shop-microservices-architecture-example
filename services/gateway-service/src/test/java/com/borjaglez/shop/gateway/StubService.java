package com.borjaglez.shop.gateway;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Tiny downstream service that records what the gateway forwards. */
final class StubService implements AutoCloseable {

  record Received(String method, String pathAndQuery, Map<String, List<String>> headers) {
    String header(String name) {
      return headers.entrySet().stream()
          .filter(e -> e.getKey().equalsIgnoreCase(name))
          .map(e -> String.join(",", e.getValue()))
          .findFirst()
          .orElse(null);
    }
  }

  private final HttpServer server;
  private final List<Received> received = new CopyOnWriteArrayList<>();

  StubService() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/", this::handle);
    server.start();
  }

  String baseUrl() {
    return "http://localhost:" + server.getAddress().getPort();
  }

  List<Received> received() {
    return received;
  }

  private void handle(HttpExchange exchange) throws IOException {
    String query = exchange.getRequestURI().getRawQuery();
    received.add(
        new Received(
            exchange.getRequestMethod(),
            exchange.getRequestURI().getRawPath() + (query == null ? "" : "?" + query),
            Map.copyOf(exchange.getRequestHeaders())));
    byte[] body = "{\"from\":\"stub\"}".getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    // Like the real services, echo the correlation id.
    String correlationId = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
    if (correlationId != null) {
      exchange.getResponseHeaders().add("X-Correlation-Id", correlationId);
    }
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
