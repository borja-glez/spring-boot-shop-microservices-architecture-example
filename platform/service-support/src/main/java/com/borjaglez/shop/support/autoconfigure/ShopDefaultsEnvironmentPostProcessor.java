package com.borjaglez.shop.support.autoconfigure;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Registers the defaults every shop service shares, with the lowest precedence so any {@code
 * application.yaml}, profile or environment variable can override them.
 */
public class ShopDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

  static final String PROPERTY_SOURCE_NAME = "shopDefaults";

  private static final Map<String, Object> DEFAULTS =
      Map.ofEntries(
          // Never let a client ask for an unbounded page.
          Map.entry("spring.data.web.pageable.max-page-size", 100),
          Map.entry("spring.data.web.pageable.default-page-size", 20),
          // Bounds of the HTTP filter syntax of specification-repository, tighter than the
          // library's (20 filters, 5 sort fields, 100 values, 1000 characters): no shop screen
          // sends more than a few filters, two sort fields, a list of a few ids or a short text.
          // Above them the parser answers 400 before any SQL runs.
          Map.entry("specrepository.http.max-filters", 10),
          Map.entry("specrepository.http.max-sort-fields", 3),
          Map.entry("specrepository.http.max-values-per-filter", 50),
          Map.entry("specrepository.http.max-value-length", 200),
          // Operators a client may use: comparisons and lists, which an index can serve. A service
          // that offers text search adds contains, startswith... in its application.yaml.
          Map.entry(
              "specrepository.http.allowed-operators",
              "eq,neq,in,notin,gt,gte,lt,lte,between,isnull,isnotnull"),
          // The library's 400 advice is off: SpecificationProblemMapper answers its exceptions with
          // the shop's problem shape (code, type, correlationId), as every other error.
          Map.entry("specrepository.http.problem-details.enabled", false),
          Map.entry("spring.jpa.open-in-view", false),
          // PostgreSQL waits forever on a socket by default: when the database pod goes away, a
          // query on a connection to its old address never returns and the thread that ran it
          // (the saga runner, the relay) hangs for good. Give up after 30 s, and probe idle
          // connections.
          Map.entry("spring.datasource.hikari.data-source-properties.socketTimeout", 30),
          Map.entry("spring.datasource.hikari.data-source-properties.tcpKeepAlive", true),
          Map.entry("spring.threads.virtual.enabled", true),
          Map.entry("server.shutdown", "graceful"),
          Map.entry("spring.lifecycle.timeout-per-shutdown-phase", "20s"),
          Map.entry(
              "management.endpoints.web.exposure.include", "health,info,prometheus,metrics,cqrs"),
          Map.entry("management.endpoint.health.probes.enabled", true),
          Map.entry("management.endpoint.health.show-details", "always"),
          Map.entry("management.info.env.enabled", true),
          // Native images: the contracts a service only sends or records also need hints.
          Map.entry("cqrs.aot.message-packages", "com.borjaglez.shop.contracts"),
          // Flyway creates the tables of spring-boot-cqrs-jdbc (es-kit's db/eskit).
          Map.entry("cqrs.jdbc.initialize-schema", "never"),
          // Every request is traced: a demo wants whole traces, not a 10% sample.
          Map.entry("management.tracing.sampling.probability", 1.0),
          // Latency histograms (buckets) for the timers the dashboards and alerts use; without
          // them the OTLP registry sends only a count and a sum, and no percentile can be computed.
          Map.entry(
              "management.metrics.distribution.percentiles-histogram.http.server.requests", true),
          Map.entry("management.metrics.distribution.percentiles-histogram.cqrs.bus", true),
          Map.entry("management.metrics.distribution.percentiles-histogram.spring.rabbit", true),
          Map.entry("management.metrics.distribution.percentiles-histogram.shop", true),
          // A checkout may retry for about two minutes before it is rejected.
          Map.entry(
              "management.metrics.distribution.maximum-expected-value.shop.checkout.duration",
              "5m"),
          // Carry the trace across the brokers (W3C traceparent header) where the listener
          // containers and templates are Boot's; cqrs builds some of its own.
          Map.entry("spring.rabbitmq.listener.simple.observation-enabled", true),
          Map.entry("spring.rabbitmq.template.observation-enabled", true),
          Map.entry("spring.kafka.listener.observation-enabled", true),
          Map.entry("spring.kafka.template.observation-enabled", true),
          Map.entry("logging.pattern.correlation", "[%X{correlationId:-},%X{traceId:-}] "));

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (!environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
      environment
          .getPropertySources()
          .addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, DEFAULTS));
    }
  }
}
