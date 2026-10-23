package com.borjaglez.shop.support.web;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The operations of the API that are deprecated ({@code shop.api.deprecations[n]}).
 *
 * @param deprecations one entry per deprecated operation, matched by method and path pattern
 */
@ConfigurationProperties("shop.api")
public record ApiDeprecationProperties(@Nullable List<Deprecation> deprecations) {

  public ApiDeprecationProperties {
    deprecations = deprecations == null ? List.of() : List.copyOf(deprecations);
  }

  /**
   * One deprecated operation.
   *
   * @param method HTTP method, such as {@code GET}
   * @param path Ant-style path pattern, such as {@code /api/v1/catalog/products/**}
   * @param deprecatedAt when the operation was deprecated
   * @param sunset when the operation stops being available, if known
   * @param successor URI of the operation or documentation that replaces it, if any
   */
  public record Deprecation(
      String method,
      String path,
      Instant deprecatedAt,
      @Nullable Instant sunset,
      @Nullable String successor) {}
}
