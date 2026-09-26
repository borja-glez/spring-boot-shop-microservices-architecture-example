package com.borjaglez.shop.support.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import com.borjaglez.shop.support.tracing.MicrometerTraceCarrier;
import com.borjaglez.shop.support.tracing.TraceCarrier;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

/**
 * The {@link TraceCarrier} of the service: Micrometer Tracing when the application has a tracer and
 * a propagator, a no-op otherwise. The beans are looked up when the carrier is created, so the
 * order of the tracing auto-configurations does not matter.
 */
@AutoConfiguration
public class ShopTracingAutoConfiguration {

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
  static class MicrometerTracing {

    @Bean
    @ConditionalOnMissingBean
    TraceCarrier traceCarrier(
        ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
      Tracer t = tracer.getIfAvailable();
      Propagator p = propagator.getIfAvailable();
      return t != null && p != null ? new MicrometerTraceCarrier(t, p) : TraceCarrier.NONE;
    }
  }

  /**
   * Kubernetes probes the health endpoints every few seconds: without this, most traces in Grafana
   * would be {@code GET /actuator/health/**}.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(
      name = "org.springframework.http.server.observation.ServerRequestObservationContext")
  static class ActuatorRequests {

    @Bean
    ObservationPredicate noActuatorObservations() {
      return (name, context) ->
          !(context instanceof ServerRequestObservationContext request
              && pathOf(request).startsWith("/actuator"));
    }
  }

  /** The path inside the application, whatever its context path. */
  static String pathOf(ServerRequestObservationContext request) {
    String uri = request.getCarrier().getRequestURI();
    return uri.substring(request.getCarrier().getContextPath().length());
  }

  @Bean
  @ConditionalOnMissingBean
  TraceCarrier noTraceCarrier() {
    return TraceCarrier.NONE;
  }
}
