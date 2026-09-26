package com.borjaglez.shop.support.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

class ShopOtlpAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ShopOtlpAutoConfiguration.class));

  @Test
  void theEndpointIsReadWhenTheApplicationStarts() {
    runner
        .withPropertyValues("OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4318/")
        .run(
            context ->
                assertThat(context.getBean(SpanExporter.class))
                    .isInstanceOf(OtlpHttpSpanExporter.class)
                    .asString()
                    .contains("http://lgtm:4318/v1/traces"));
  }

  @Test
  void withoutAnEndpointTracesGoNowhere() {
    runner.run(
        context ->
            assertThat(context.getBean(SpanExporter.class))
                .isNotInstanceOf(OtlpHttpSpanExporter.class));
  }

  @Test
  void logsGoToTheSameCollector() {
    runner
        .withPropertyValues("OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4318")
        .run(
            context ->
                assertThat(context.getBean(LogRecordExporter.class))
                    .isInstanceOf(OtlpHttpLogRecordExporter.class)
                    .asString()
                    .contains("http://lgtm:4318/v1/logs"));
  }

  @Test
  void withoutAnEndpointLogsStayOnTheConsole() {
    runner.run(
        context -> {
          assertThat(context.getBean(LogRecordExporter.class))
              .isNotInstanceOf(OtlpHttpLogRecordExporter.class);
          assertThat(context).hasSingleBean(OtlpLogAppenderInstaller.class);
        });
  }
}
