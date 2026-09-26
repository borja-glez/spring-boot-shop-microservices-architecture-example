package com.borjaglez.shop.support.autoconfigure;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.borjaglez.shop.support.chaos.Chaos;
import com.borjaglez.shop.support.chaos.ChaosController;
import com.borjaglez.shop.support.chaos.MessageChaosMiddleware;

/**
 * Fault injection for the demo. The {@link Chaos} registry always exists, so components can
 * register and check their faults unconditionally. The endpoint that turns them on exists in the
 * services that name themselves in {@code shop.chaos.service} and answers only with {@code
 * shop.chaos.enabled=true}.
 */
@AutoConfiguration
public class ShopChaosAutoConfiguration {

  static final String MESSAGES = "shop.chaos.messages";

  @Bean
  @ConditionalOnMissingBean
  Chaos chaos() {
    return new Chaos();
  }

  @Bean
  @ConditionalOnProperty("shop.chaos.service")
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  ChaosController chaosController(Chaos chaos, Environment environment) {
    return new ChaosController(
        chaos, environment.getProperty("shop.chaos.enabled", Boolean.class, false));
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "com.borjaglez.cqrs.middleware.BusMiddleware")
  static class MessageFaults {

    @Bean
    MessageChaosMiddleware messageChaosMiddleware(Chaos chaos, Environment environment) {
      List<String> types =
          Binder.get(environment).bind(MESSAGES, String[].class).map(List::of).orElse(List.of());
      return new MessageChaosMiddleware(chaos, types);
    }
  }
}
