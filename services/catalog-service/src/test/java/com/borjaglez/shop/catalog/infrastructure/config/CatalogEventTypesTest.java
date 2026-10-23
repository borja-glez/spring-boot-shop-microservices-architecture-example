package com.borjaglez.shop.catalog.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.catalog.domain.event.ProductDiscontinued;
import com.borjaglez.shop.catalog.domain.event.ProductEvent;
import com.borjaglez.shop.catalog.domain.event.ProductPriceChanged;
import com.borjaglez.shop.catalog.domain.event.ProductPublished;
import com.borjaglez.shop.eskit.DomainEventTypes;

class CatalogEventTypesTest {

  private final DomainEventTypes types = CatalogEventTypes.domainEventTypes();

  @Test
  void storesEachProductEventUnderAVersionedName() {
    assertThat(types.nameOf(ProductPublished.class)).isEqualTo("product-published.v1");
    assertThat(types.nameOf(ProductPriceChanged.class)).isEqualTo("product-price-changed.v1");
    assertThat(types.nameOf(ProductDiscontinued.class)).isEqualTo("product-discontinued.v1");
  }

  @Test
  void everyProductEventIsRegistered() {
    Set<Class<?>> registered = new HashSet<>(types.types());

    assertThat(registered).containsExactlyInAnyOrder(ProductEvent.class.getPermittedSubclasses());
  }
}
