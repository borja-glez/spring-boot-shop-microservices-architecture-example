package com.borjaglez.shop.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

class DefaultRoutesTest {

  @Test
  void everyServiceHasAnUnversionedAndAVersionedRoute() throws Exception {
    var sources = new MutablePropertySources();
    new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yaml"))
        .forEach(sources::addLast);
    GatewayRoutesProperties properties =
        new Binder(
                ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources))
            .bind("shop.gateway", GatewayRoutesProperties.class)
            .get();

    var paths = properties.routes().stream().map(GatewayRoutesProperties.Route::path).toList();
    for (String service :
        List.of("catalog", "orders", "inventory", "payments", "notifications", "reporting")) {
      assertThat(paths).contains("/api/" + service + "/**", "/api/v1/" + service + "/**");
    }
    assertThat(paths).contains("/api/v2/orders/**");
    assertThat(paths).doesNotContain("/api/**");
  }
}
