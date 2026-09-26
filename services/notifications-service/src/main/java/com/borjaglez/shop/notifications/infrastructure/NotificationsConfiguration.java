package com.borjaglez.shop.notifications.infrastructure;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class NotificationsConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
