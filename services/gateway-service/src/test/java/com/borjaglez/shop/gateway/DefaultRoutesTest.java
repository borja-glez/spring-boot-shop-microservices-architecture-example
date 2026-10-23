package com.borjaglez.shop.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

import com.borjaglez.shop.gateway.GatewayRoutesProperties.Route;

class DefaultRoutesTest {

  @Test
  void everyServiceHasAnUnversionedAndAVersionedRoute() throws Exception {
    var paths = defaultRoutes().stream().map(Route::path).toList();
    for (String service :
        List.of("catalog", "orders", "inventory", "payments", "notifications", "reporting")) {
      assertThat(paths).contains("/api/" + service + "/**", "/api/v1/" + service + "/**");
    }
    assertThat(paths).contains("/api/v2/orders/**");
    assertThat(paths).doesNotContain("/api/**");
  }

  /** The services that set {@code shop.chaos.service} serve the chaos API of service-support. */
  @Test
  void everyServiceWithFaultInjectionHasAChaosRouteToItself() throws Exception {
    Map<String, URI> uriByPath =
        defaultRoutes().stream().collect(Collectors.toMap(Route::path, Route::uri));

    for (String service : List.of("catalog", "orders", "inventory", "payments")) {
      assertThat(uriByPath)
          .containsEntry(
              "/api/v1/chaos/" + service + "/**", uriByPath.get("/api/v1/" + service + "/**"));
    }
    assertThat(uriByPath).doesNotContainKeys("/api/v1/chaos/**", "/api/v1/chaos/notifications/**");
  }

  private static List<Route> defaultRoutes() throws IOException {
    var sources = new MutablePropertySources();
    new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yaml"))
        .forEach(sources::addLast);
    return new Binder(
            ConfigurationPropertySources.from(sources),
            new PropertySourcesPlaceholdersResolver(sources))
        .bind("shop.gateway", GatewayRoutesProperties.class)
        .get()
        .routes();
  }
}
