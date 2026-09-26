package com.borjaglez.shop.support.autoconfigure;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.context.aot.AbstractAotProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Turns the standard {@code OTEL_EXPORTER_OTLP_ENDPOINT} variable into the OTLP metrics settings of
 * Spring Boot (traces go through {@link ShopOtlpAutoConfiguration}), so one variable sends
 * everything to a collector such as {@code grafana/otel-lgtm}.
 *
 * <p>Without it nothing is exported: the OTLP meter registry, which Boot enables by default, is
 * switched off instead of retrying {@code localhost:4318} every minute. A native image evaluates
 * the conditions of the auto-configurations at build time, so during AOT processing the registry is
 * left on: it is compiled in, and the same property, bound at runtime, decides whether it pushes.
 * Everything goes in with the lowest precedence, so explicit properties win.
 */
public class OtlpExportEnvironmentPostProcessor implements EnvironmentPostProcessor {

  static final String ENDPOINT_VARIABLE = "OTEL_EXPORTER_OTLP_ENDPOINT";
  static final String PROPERTY_SOURCE_NAME = "shopOtlpExport";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
      return;
    }
    environment
        .getPropertySources()
        .addLast(
            new MapPropertySource(
                PROPERTY_SOURCE_NAME,
                settings(
                    environment.getProperty(ENDPOINT_VARIABLE),
                    Boolean.getBoolean(AbstractAotProcessor.AOT_PROCESSING))));
  }

  static Map<String, Object> settings(String endpoint, boolean aotProcessing) {
    if (StringUtils.hasText(endpoint)) {
      return Map.of(
          "management.otlp.metrics.export.url",
          base(endpoint) + "/v1/metrics",
          "management.otlp.metrics.export.step",
          "10s");
    }
    return aotProcessing ? Map.of() : Map.of("management.otlp.metrics.export.enabled", false);
  }

  static String base(String endpoint) {
    return endpoint.strip().replaceAll("/+$", "");
  }
}
