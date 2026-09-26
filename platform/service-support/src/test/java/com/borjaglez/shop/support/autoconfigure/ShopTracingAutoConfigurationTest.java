package com.borjaglez.shop.support.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.borjaglez.shop.support.tracing.MicrometerTraceCarrier;
import com.borjaglez.shop.support.tracing.TraceCarrier;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

class ShopTracingAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ShopTracingAutoConfiguration.class));

  @Test
  void usesMicrometerTracingWhenTheServiceTraces() {
    runner
        .withBean(Tracer.class, () -> mock(Tracer.class))
        .withBean(Propagator.class, () -> mock(Propagator.class))
        .run(
            context ->
                assertThat(context.getBean(TraceCarrier.class))
                    .isInstanceOf(MicrometerTraceCarrier.class));
  }

  @Test
  void carriesNothingWithoutATracerOrAPropagator() {
    runner.run(
        context -> assertThat(context.getBean(TraceCarrier.class)).isSameAs(TraceCarrier.NONE));
    runner
        .withBean(Tracer.class, () -> mock(Tracer.class))
        .run(
            context -> assertThat(context.getBean(TraceCarrier.class)).isSameAs(TraceCarrier.NONE));
  }

  @Test
  void carriesNothingWithoutMicrometerTracing() {
    runner
        .withClassLoader(new FilteredClassLoader("io.micrometer.tracing"))
        .run(
            context -> assertThat(context.getBean(TraceCarrier.class)).isSameAs(TraceCarrier.NONE));
  }

  @Test
  void healthProbesAreNotObserved() {
    runner.run(
        context -> {
          ObservationPredicate predicate = context.getBean(ObservationPredicate.class);
          assertThat(predicate.test("http.server.requests", request("/actuator/health/liveness")))
              .isFalse();
          assertThat(predicate.test("http.server.requests", request("/api/orders"))).isTrue();
          MockHttpServletRequest behindContextPath =
              new MockHttpServletRequest("GET", "/shop/actuator/health");
          behindContextPath.setContextPath("/shop");
          assertThat(
                  predicate.test(
                      "http.server.requests",
                      new ServerRequestObservationContext(
                          behindContextPath, new MockHttpServletResponse())))
              .isFalse();
          assertThat(predicate.test("cqrs.bus.handle", new Observation.Context())).isTrue();
        });
  }

  private static ServerRequestObservationContext request(String uri) {
    return new ServerRequestObservationContext(
        new MockHttpServletRequest("GET", uri), new MockHttpServletResponse());
  }
}
