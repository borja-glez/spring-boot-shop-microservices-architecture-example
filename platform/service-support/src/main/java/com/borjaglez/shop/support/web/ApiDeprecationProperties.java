package com.borjaglez.shop.support.web;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The operations of the API that are deprecated ({@code shop.api.deprecations[n]}).
 *
 * @param deprecations one entry per deprecated operation, matched by method and path pattern; a
 *     request that matches several gets the headers of the first one
 */
@ConfigurationProperties("shop.api")
public record ApiDeprecationProperties(@Nullable List<Deprecation> deprecations) {

  public ApiDeprecationProperties {
    deprecations = deprecations == null ? List.of() : List.copyOf(deprecations);
  }

  /**
   * One deprecated operation. An incomplete entry, or one whose sunset comes before its
   * deprecation, stops the application at startup.
   *
   * @param method HTTP method, such as {@code GET}; a {@code GET} entry covers {@code HEAD} too
   * @param path Ant-style path pattern, such as {@code /api/v1/catalog/products/**}
   * @param deprecatedAt when the operation was deprecated
   * @param sunset when the operation stops being available, if known; not before {@code
   *     deprecatedAt}
   * @param successor URI of the operation or documentation that replaces it, if any
   */
  public record Deprecation(
      String method,
      String path,
      Instant deprecatedAt,
      @Nullable Instant sunset,
      @Nullable String successor) {

    public Deprecation {
      if (method == null || method.isBlank()) {
        throw new IllegalArgumentException("A deprecation needs a method, such as GET");
      }
      if (path == null || path.isBlank()) {
        throw new IllegalArgumentException(
            "The deprecation of " + method + " needs a path, such as /api/v1/things/**");
      }
      if (deprecatedAt == null) {
        throw new IllegalArgumentException(
            "The deprecation of " + method + " " + path + " needs deprecated-at");
      }
      if (sunset != null && sunset.isBefore(deprecatedAt)) {
        throw new IllegalArgumentException(
            "The deprecation of %s %s has its sunset (%s) before deprecated-at (%s)"
                .formatted(method, path, sunset, deprecatedAt));
      }
    }
  }
}
