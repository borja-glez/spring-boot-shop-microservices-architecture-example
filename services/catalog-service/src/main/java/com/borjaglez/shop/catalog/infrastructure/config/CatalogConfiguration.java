package com.borjaglez.shop.catalog.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.shop.catalog.domain.CategoryJpaRepository;
import com.borjaglez.shop.catalog.domain.CategoryRepository;
import com.borjaglez.shop.catalog.domain.ProductJpaRepository;
import com.borjaglez.shop.catalog.domain.ProductRepository;
import com.borjaglez.shop.catalog.domain.SellerJpaRepository;
import com.borjaglez.shop.catalog.domain.SellerRepository;
import com.borjaglez.shop.catalog.infrastructure.out.messaging.CatalogIntegrationEvents;
import com.borjaglez.shop.catalog.infrastructure.out.persistence.JpaCategoryRepository;
import com.borjaglez.shop.catalog.infrastructure.out.persistence.JpaProductRepository;
import com.borjaglez.shop.catalog.infrastructure.out.persistence.JpaSellerRepository;
import com.borjaglez.shop.eskit.DomainEventStore;
import com.borjaglez.shop.eskit.IntegrationEventTranslator;

/**
 * [Hexagonal · composition root] Plugs the adapters of the catalog into the ports of its core.
 *
 * <p>The use cases are the {@code @CommandHandler} classes of {@code application.service}, which
 * spring-boot-cqrs registers and runs each in a transaction of its own ({@code
 * cqrs.transactions.enabled}); the adapters join it. With {@link CatalogEventTypes} and the
 * translator declared here, es-kit provides the {@link DomainEventStore}.
 */
@Configuration(proxyBeanMethods = false)
public class CatalogConfiguration {

  /** Time source of the domain. Tests replace it to control dates. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  ProductRepository productRepository(
      ProductJpaRepository products,
      SellerJpaRepository sellers,
      CategoryJpaRepository categories,
      DomainEventStore events) {
    return new JpaProductRepository(products, sellers, categories, events);
  }

  @Bean
  SellerRepository sellerRepository(SellerJpaRepository sellers) {
    return new JpaSellerRepository(sellers);
  }

  @Bean
  CategoryRepository categoryRepository(CategoryJpaRepository categories) {
    return new JpaCategoryRepository(categories);
  }

  @Bean
  IntegrationEventTranslator catalogIntegrationEvents() {
    return new CatalogIntegrationEvents();
  }
}
