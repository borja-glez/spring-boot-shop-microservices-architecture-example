package com.borjaglez.shop.support.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class OtlpExportEnvironmentPostProcessorTest {

  private final OtlpExportEnvironmentPostProcessor processor =
      new OtlpExportEnvironmentPostProcessor();

  private StandardEnvironment environment(Map<String, Object> properties) {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    processor.postProcessEnvironment(environment, new SpringApplication());
    return environment;
  }

  @Test
  void withoutAnEndpointNoMetricsArePushed() {
    assertThat(environment(Map.of()).getProperty("management.otlp.metrics.export.enabled"))
        .isEqualTo("false");
    assertThat(
            environment(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", " "))
                .getProperty("management.otlp.metrics.export.enabled"))
        .isEqualTo("false");
  }

  @Test
  void duringAotProcessingTheRegistryStaysInTheImage() {
    assertThat(OtlpExportEnvironmentPostProcessor.settings(null, true)).isEmpty();
  }

  @Test
  void oneEndpointSendsTheMetrics() {
    StandardEnvironment environment =
        environment(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://lgtm:4318/"));

    assertThat(environment.getProperty("management.otlp.metrics.export.url"))
        .isEqualTo("http://lgtm:4318/v1/metrics");
    assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isNull();
  }

  @Test
  void explicitPropertiesWin() {
    StandardEnvironment environment =
        environment(
            Map.of(
                "OTEL_EXPORTER_OTLP_ENDPOINT", "http://lgtm:4318",
                "management.otlp.metrics.export.url", "http://other:4318/v1/metrics"));

    assertThat(environment.getProperty("management.otlp.metrics.export.url"))
        .isEqualTo("http://other:4318/v1/metrics");
  }

  @Test
  void runningTwiceAddsTheSettingsOnce() {
    StandardEnvironment environment = environment(Map.of());
    processor.postProcessEnvironment(environment, new SpringApplication());

    assertThat(
            environment.getPropertySources().stream()
                .filter(
                    s ->
                        s.getName().equals(OtlpExportEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .count())
        .isEqualTo(1);
  }
}
