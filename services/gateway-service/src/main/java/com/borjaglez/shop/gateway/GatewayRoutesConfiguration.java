package com.borjaglez.shop.gateway;

import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.removeResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import com.borjaglez.shop.gateway.GatewayRoutesProperties.Route;
import com.borjaglez.shop.support.web.CorrelationIdFilter;
import com.borjaglez.shop.support.web.problem.MappedProblem;
import com.borjaglez.shop.support.web.problem.ProblemMapper;

/** Builds one gateway route per configured service. */
@Configuration(proxyBeanMethods = false)
class GatewayRoutesConfiguration {

  @Bean
  RouterFunction<ServerResponse> serviceRoutes(GatewayRoutesProperties properties) {
    return properties.routes().stream()
        .map(GatewayRoutesConfiguration::toRouterFunction)
        .reduce(RouterFunction::and)
        .orElseThrow();
  }

  private static RouterFunction<ServerResponse> toRouterFunction(Route route) {
    return route(route.id())
        .route(path(route.path()), http())
        .before(uri(route.uri()))
        .before(propagateCorrelationId())
        // The gateway already answers with the id; drop the service's copy to avoid a duplicate.
        .after(removeResponseHeader(CorrelationIdFilter.HEADER))
        .build();
  }

  /**
   * The shared correlation filter may have generated the id in this gateway; forward it so every
   * service logs the same id for the request.
   */
  static Function<ServerRequest, ServerRequest> propagateCorrelationId() {
    return request -> {
      Optional<Object> id = request.attribute(CorrelationIdFilter.ATTRIBUTE);
      if (id.isEmpty()) {
        return request;
      }
      return ServerRequest.from(request)
          .headers(headers -> headers.set(CorrelationIdFilter.HEADER, id.get().toString()))
          .build();
    };
  }

  /** Runs before the request is observed, so the trace starts here (see the filter). */
  @Bean
  FilterRegistrationBean<ExternalTraceHeadersFilter> externalTraceHeadersFilter() {
    var registration = new FilterRegistrationBean<>(new ExternalTraceHeadersFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  /** A service that does not answer is reported as 503, not as an internal gateway error. */
  @Bean
  @Order(100)
  ProblemMapper unreachableServiceProblemMapper() {
    return exception -> {
      if (exception instanceof ConnectException
          || exception instanceof HttpConnectTimeoutException
          || exception instanceof ResourceAccessException) {
        return Optional.of(
            MappedProblem.of(
                HttpStatus.SERVICE_UNAVAILABLE,
                "service-unavailable",
                "The service behind this route is not reachable. Try again in a moment."));
      }
      return Optional.empty();
    };
  }
}
