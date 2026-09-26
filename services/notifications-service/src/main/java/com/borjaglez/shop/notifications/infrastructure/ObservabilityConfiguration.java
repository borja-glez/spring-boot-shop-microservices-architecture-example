package com.borjaglez.shop.notifications.infrastructure;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.util.StringUtils;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

/**
 * The same telemetry as the Boot 4 services, which get it from service-support: Kafka consumer
 * metrics (lag included) for the consumers spring-boot-cqrs creates, and log records over OTLP next
 * to traces and metrics when {@code management.otlp.logging.endpoint} is set.
 */
@Configuration(proxyBeanMethods = false)
class ObservabilityConfiguration {

  static final String LOGS_ENDPOINT = "management.otlp.logging.endpoint";

  @Bean
  static BeanPostProcessor cqrsKafkaConsumerMetrics(ObjectProvider<MeterRegistry> meters) {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DefaultKafkaConsumerFactory<?, ?> consumers
            && consumers.getListeners().stream()
                .noneMatch(MicrometerConsumerListener.class::isInstance)) {
          meters.ifAvailable(
              registry -> consumers.addListener(new MicrometerConsumerListener<>(registry)));
        }
        return bean;
      }
    };
  }

  @Bean
  SmartInitializingSingleton otlpLogAppender(
      Environment environment, ObjectProvider<OpenTelemetry> openTelemetry) {
    return () -> {
      OpenTelemetry sdk = openTelemetry.getIfAvailable();
      if (sdk != null
          && StringUtils.hasText(environment.getProperty(LOGS_ENDPOINT))
          && LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        if (root.getAppender("OTLP") == null) {
          OpenTelemetryAppender appender = new OpenTelemetryAppender();
          appender.setName("OTLP");
          appender.setContext(context);
          appender.start();
          root.addAppender(appender);
        }
        OpenTelemetryAppender.install(sdk);
      }
    };
  }
}
