package com.borjaglez.shop.support.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.OpenTelemetry;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

class OtlpLogAppenderInstallerTest {

  private final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

  @AfterEach
  void removeAppender() {
    root().detachAppender(OtlpLogAppenderInstaller.APPENDER_NAME);
  }

  private Logger root() {
    return context.getLogger(Logger.ROOT_LOGGER_NAME);
  }

  @Test
  void addsTheAppenderToTheRootLoggerOnce() {
    assertThat(OtlpLogAppenderInstaller.install(context, OpenTelemetry.noop())).isTrue();
    assertThat(OtlpLogAppenderInstaller.install(context, OpenTelemetry.noop())).isTrue();

    assertThat(root().getAppender(OtlpLogAppenderInstaller.APPENDER_NAME)).isNotNull();
    assertThat(root().iteratorForAppenders())
        .toIterable()
        .filteredOn(a -> OtlpLogAppenderInstaller.APPENDER_NAME.equals(a.getName()))
        .hasSize(1);
  }

  @Test
  void onlyLogbackIsSupported() {
    ILoggerFactory foreign = name -> null;

    assertThat(OtlpLogAppenderInstaller.install(foreign, OpenTelemetry.noop())).isFalse();
  }

  @Test
  void nothingIsInstalledWhenExportIsOff() {
    new OtlpLogAppenderInstaller(false, new StaticProvider(OpenTelemetry.noop()))
        .afterSingletonsInstantiated();

    assertThat(root().getAppender(OtlpLogAppenderInstaller.APPENDER_NAME)).isNull();
  }

  @Test
  void theAppenderIsInstalledWhenExportIsOn() {
    new OtlpLogAppenderInstaller(true, new StaticProvider(OpenTelemetry.noop()))
        .afterSingletonsInstantiated();

    assertThat(root().getAppender(OtlpLogAppenderInstaller.APPENDER_NAME)).isNotNull();
  }

  private record StaticProvider(OpenTelemetry value)
      implements org.springframework.beans.factory.ObjectProvider<OpenTelemetry> {

    @Override
    public OpenTelemetry getObject() {
      return value;
    }
  }
}
