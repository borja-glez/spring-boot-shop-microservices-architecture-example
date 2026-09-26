package com.borjaglez.shop.support.autoconfigure;

import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

/**
 * Adds the OpenTelemetry appender to the root logger once the application's {@link OpenTelemetry}
 * exists, so every log line is also exported as an OTLP log record. Nothing changes when export is
 * off. The request's correlation id travels as a log attribute next to the trace context.
 */
public class OtlpLogAppenderInstaller implements SmartInitializingSingleton {

  static final String APPENDER_CLASS =
      "io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender";
  static final String APPENDER_NAME = "OTLP";

  private final boolean enabled;
  private final ObjectProvider<OpenTelemetry> openTelemetry;

  public OtlpLogAppenderInstaller(boolean enabled, ObjectProvider<OpenTelemetry> openTelemetry) {
    this.enabled = enabled;
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void afterSingletonsInstantiated() {
    OpenTelemetry sdk = openTelemetry.getIfAvailable();
    if (enabled && sdk != null) {
      install(LoggerFactory.getILoggerFactory(), sdk);
    }
  }

  /** Whether the appender was installed: only Logback is supported. */
  static boolean install(ILoggerFactory loggerFactory, OpenTelemetry sdk) {
    if (!(loggerFactory instanceof LoggerContext context)) {
      return false;
    }
    Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
    if (root.getAppender(APPENDER_NAME) == null) {
      OpenTelemetryAppender appender = new OpenTelemetryAppender();
      appender.setName(APPENDER_NAME);
      appender.setContext(context);
      appender.setCaptureMdcAttributes("correlationId");
      appender.start();
      root.addAppender(appender);
    }
    OpenTelemetryAppender.install(sdk);
    return true;
  }
}
