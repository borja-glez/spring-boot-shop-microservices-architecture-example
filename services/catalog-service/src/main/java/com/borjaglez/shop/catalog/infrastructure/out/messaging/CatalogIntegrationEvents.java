package com.borjaglez.shop.catalog.infrastructure.out.messaging;

import java.util.List;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.shop.catalog.domain.event.ProductDiscontinued;
import com.borjaglez.shop.catalog.domain.event.ProductEvent;
import com.borjaglez.shop.catalog.domain.event.ProductPriceChanged;
import com.borjaglez.shop.catalog.domain.event.ProductPublished;
import com.borjaglez.shop.eskit.IntegrationEventTranslator;
import com.borjaglez.shop.kernel.domain.DomainEvent;

/**
 * [Hexagonal · adaptador driven] [Context map · Published Language] Turns the events of a {@code
 * Product} into the catalog events of {@code platform/contracts} that orders, inventory, reporting
 * and notifications receive. Every product event concerns them, so each one becomes exactly one
 * integration event.
 */
public class CatalogIntegrationEvents implements IntegrationEventTranslator {

  @Override
  public List<Event> translate(DomainEvent event) {
    if (!(event instanceof ProductEvent productEvent)) {
      throw new IllegalArgumentException(
          event.getClass().getName() + " is not a catalog event: only product events are stored");
    }
    return List.of(
        switch (productEvent) {
          case ProductPublished published ->
              new com.borjaglez.shop.contracts.catalog.ProductPublished(
                  published.id().value(),
                  published.sku().value(),
                  published.name(),
                  published.price().amount(),
                  published.price().currency(),
                  published.seller().value());
          case ProductPriceChanged changed ->
              new com.borjaglez.shop.contracts.catalog.ProductPriceChanged(
                  changed.id().value(),
                  changed.oldPrice().amount(),
                  changed.newPrice().amount(),
                  changed.newPrice().currency());
          case ProductDiscontinued discontinued ->
              new com.borjaglez.shop.contracts.catalog.ProductDiscontinued(
                  discontinued.id().value(), discontinued.reason());
        });
  }
}
