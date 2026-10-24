package com.borjaglez.shop.support.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import com.borjaglez.shop.support.autoconfigure.ShopWebAutoConfiguration;
import com.borjaglez.shop.support.web.ApiDeprecationProperties.Deprecation;

class ApiDeprecationPropertiesTest {

  private static final Instant DEPRECATED_AT = Instant.parse("2026-01-15T00:00:00Z");
  private static final Instant SUNSET = Instant.parse("2026-07-01T00:00:00Z");

  @Test
  void acceptsACompleteEntry() {
    var deprecation =
        new Deprecation("GET", "/api/v1/things/**", DEPRECATED_AT, SUNSET, "https://x/v2");

    assertThat(deprecation.sunset()).isEqualTo(SUNSET);
    assertThat(new Deprecation("GET", "/api/v1/things", DEPRECATED_AT, null, null).sunset())
        .isNull();
    assertThat(new Deprecation("GET", "/api/v1/things", SUNSET, SUNSET, null).sunset())
        .isEqualTo(SUNSET);
  }

  @Test
  void requiresTheMethod() {
    assertThatThrownBy(() -> new Deprecation(null, "/api/v1/things", DEPRECATED_AT, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("method");
    assertThatThrownBy(() -> new Deprecation(" ", "/api/v1/things", DEPRECATED_AT, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("method");
  }

  @Test
  void requiresThePath() {
    assertThatThrownBy(() -> new Deprecation("GET", null, DEPRECATED_AT, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path");
    assertThatThrownBy(() -> new Deprecation("GET", "", DEPRECATED_AT, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path");
  }

  @Test
  void requiresTheDeprecationDate() {
    assertThatThrownBy(() -> new Deprecation("GET", "/api/v1/things", null, SUNSET, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("deprecated-at")
        .hasMessageContaining("GET /api/v1/things");
  }

  @Test
  void rejectsASunsetBeforeTheDeprecation() {
    assertThatThrownBy(() -> new Deprecation("GET", "/api/v1/things", SUNSET, DEPRECATED_AT, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sunset")
        .hasMessageContaining("GET /api/v1/things");
  }

  @Test
  void anInvalidEntryStopsTheApplication() {
    new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ShopWebAutoConfiguration.class))
        .withPropertyValues(
            "shop.api.deprecations[0].method=GET",
            "shop.api.deprecations[0].path=/api/v1/things",
            "shop.api.deprecations[0].deprecated-at=2026-07-01T00:00:00Z",
            "shop.api.deprecations[0].sunset=2026-01-15T00:00:00Z")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("sunset"));
  }
}
