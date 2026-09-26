package com.borjaglez.shop.reporting.infrastructure;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ReportingConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
