package com.borjaglez.shop.catalog.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.shop.catalog.application.port.out.CatalogReadModel;
import com.borjaglez.shop.catalog.domain.CategoryRepository;
import com.borjaglez.shop.catalog.domain.ProductRepository;
import com.borjaglez.shop.catalog.domain.SellerRepository;
import com.borjaglez.shop.catalog.infrastructure.out.messaging.CatalogIntegrationEvents;
import com.borjaglez.shop.catalog.infrastructure.out.persistence.CategoryJpaRepository;
import com.borjaglez.shop.catalog.infrastructure.out.persistence.JpaCatalogReadModel;
import com.borjaglez.shop.catalog.infrastructure.out.persistence.JpaCategoryRepository;
import com.borjaglez.shop.catalog.infrastructure.out.persistence.JpaProductRepository;
import com.borjaglez.shop.catalog.infrastructure.out.persistence.JpaSellerRepository;
import com.borjaglez.shop.catalog.infrastructure.out.persistence.ProductJpaRepository;
import com.borjaglez.shop.catalog.infrastructure.out.persistence.SellerJpaRepository;
import com.borjaglez.shop.eskit.DomainEventStore;
import com.borjaglez.shop.eskit.IntegrationEventTranslator;
import com.borjaglez.specrepository.http.spring.HttpFilterProperties;

/**
 * [Hexagonal · composition root] Plugs the adapters of the catalog into the ports of its core.
 *
 * <p>The use cases are the {@code @CommandHandler} and {@code @QueryHandler} classes of {@code
 * application.service}, which spring-boot-cqrs registers. Each command runs in a transaction of the
 * bus ({@code cqrs.transactions.enabled}) that the adapters join; queries get none ({@code
 * cqrs.transactions.queries=none}), and the read side opens its own. With {@link CatalogEventTypes}
 * and the translator declared here, es-kit provides the {@link DomainEventStore}. The stock of the
 * product pages comes from {@link StockLevelsConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HttpFilterProperties.class)
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

  /**
   * The read side parses the shopper's filters with the {@code specrepository.http.*} syntax and
   * limits of the application, the same the filter API of specification-repository uses.
   */
  @Bean
  CatalogReadModel catalogReadModel(
      ProductJpaRepository products,
      CategoryJpaRepository categories,
      HttpFilterProperties filters) {
    return new JpaCatalogReadModel(products, categories, filters.toParserConfiguration());
  }

  @Bean
  IntegrationEventTranslator catalogIntegrationEvents() {
    return new CatalogIntegrationEvents();
  }
}
