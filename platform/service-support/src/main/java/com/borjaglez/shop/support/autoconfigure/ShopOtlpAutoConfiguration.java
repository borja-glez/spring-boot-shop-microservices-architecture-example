package com.borjaglez.shop.support.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Sends traces and logs to {@code OTEL_EXPORTER_OTLP_ENDPOINT} when it is set, and nowhere
 * otherwise (metrics: {@link OtlpExportEnvironmentPostProcessor}).
 *
 * <p>Spring Boot's own OTLP exporters only exist when their {@code endpoint} properties are set, a
 * condition a native image evaluates at build time: an image built without the variable could never
 * export, whatever its environment says at runtime. These exporters are always there and read the
 * variable when the application starts. Do not set Boot's properties as well: Boot would add its
 * own exporters and every span and log record would be sent twice.
 */
@AutoConfiguration
@ConditionalOnClass(OtlpHttpSpanExporter.class)
public class ShopOtlpAutoConfiguration {

  @Bean
  SpanExporter shopOtlpSpanExporter(Environment environment) {
    String endpoint = endpoint(environment);
    if (endpoint == null) {
      return SpanExporter.composite();
    }
    return OtlpHttpSpanExporter.builder().setEndpoint(endpoint + "/v1/traces").build();
  }

  /**
   * Logs travel with the traces and metrics: every log line becomes an OTLP log record that keeps
   * the trace and span ids of the request that wrote it, so Grafana jumps from a trace to its logs
   * and back. The console output stays as it is.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(
      name = {
        OtlpLogAppenderInstaller.APPENDER_CLASS,
        "ch.qos.logback.classic.LoggerContext",
        "io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter"
      })
  static class Logs {

    @Bean
    LogRecordExporter shopOtlpLogRecordExporter(Environment environment) {
      String endpoint = endpoint(environment);
      if (endpoint == null) {
        return LogRecordExporter.composite();
      }
      return OtlpHttpLogRecordExporter.builder().setEndpoint(endpoint + "/v1/logs").build();
    }

    @Bean
    OtlpLogAppenderInstaller otlpLogAppenderInstaller(
        Environment environment, ObjectProvider<OpenTelemetry> openTelemetry) {
      return new OtlpLogAppenderInstaller(endpoint(environment) != null, openTelemetry);
    }
  }

  /** The collector's base URL, without trailing slashes, or {@code null} when not configured. */
  static String endpoint(Environment environment) {
    String endpoint = environment.getProperty(OtlpExportEnvironmentPostProcessor.ENDPOINT_VARIABLE);
    return StringUtils.hasText(endpoint) ? OtlpExportEnvironmentPostProcessor.base(endpoint) : null;
  }
}
