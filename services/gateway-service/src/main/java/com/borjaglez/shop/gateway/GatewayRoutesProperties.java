package com.borjaglez.shop.gateway;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Public routes of the platform, one per service.
 *
 * <pre>
 * shop.gateway.routes[0].id=catalog
 * shop.gateway.routes[0].path=/api/catalog/**
 * shop.gateway.routes[0].uri=http://catalog-service:8081
 * </pre>
 *
 * <p>Adding a service means adding a route here (or in the environment of the deployment), never
 * touching code. The application refuses to start when a route is incomplete.
 */
@Validated
@ConfigurationProperties("shop.gateway")
public record GatewayRoutesProperties(@NotEmpty List<@Valid Route> routes) {

  /** Requests whose path matches {@code path} are forwarded, unchanged, to {@code uri}. */
  public record Route(
      @NotBlank @Pattern(regexp = "[a-z0-9-]+") String id,
      @NotBlank @Pattern(regexp = "/api/.+") String path,
      @NotNull URI uri) {}
}
