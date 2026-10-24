package com.borjaglez.shop.catalog.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.shop.catalog.domain.event.ProductDiscontinued;
import com.borjaglez.shop.catalog.domain.event.ProductPriceChanged;
import com.borjaglez.shop.catalog.domain.event.ProductPublished;
import com.borjaglez.shop.eskit.DomainEventTypes;

/**
 * [Hexagonal · composition root] The names the product events are stored under in the event store.
 * A name never changes once events are stored with it: a new shape of an event gets a new version.
 */
@Configuration(proxyBeanMethods = false)
public class CatalogEventTypes {

  /** Static and without dependencies: native builds create it ahead of time. */
  @Bean
  static DomainEventTypes domainEventTypes() {
    return DomainEventTypes.builder()
        .register("product-published.v1", ProductPublished.class)
        .register("product-price-changed.v1", ProductPriceChanged.class)
        .register("product-discontinued.v1", ProductDiscontinued.class)
        .build();
  }
}
