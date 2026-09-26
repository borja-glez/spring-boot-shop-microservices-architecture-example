package com.borjaglez.shop.notifications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The only service on Spring Boot 3.5 and Jackson 2. It reads the events that the Boot 4 services
 * write with Jackson 3, which is the point: both generations must understand the same messages.
 */
@SpringBootApplication
@EnableScheduling
public class NotificationsApplication {

  public static void main(String[] args) {
    SpringApplication.run(NotificationsApplication.class, args);
  }
}
