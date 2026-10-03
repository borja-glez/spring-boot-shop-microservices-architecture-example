package com.borjaglez.shop.orders.application.projection;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.idempotency.Idempotent;
import com.borjaglez.shop.contracts.catalog.ProductDiscontinued;
import com.borjaglez.shop.contracts.catalog.ProductPriceChanged;
import com.borjaglez.shop.contracts.catalog.ProductPublished;
import com.borjaglez.shop.orders.domain.CatalogProduct;
import com.borjaglez.shop.orders.domain.CatalogProductRepository;
import com.borjaglez.specrepository.core.Operators;

/** Keeps the local catalog copy up to date from catalog events received over Kafka. */
@EventHandler
public class CatalogProductProjector {

  static final String CONSUMER = "orders.catalog-products";

  private final CatalogProductRepository products;

  public CatalogProductProjector(CatalogProductRepository products) {
    this.products = products;
  }

  @HandleEvent
  @Idempotent(name = CONSUMER)
  @Transactional
  public void on(ProductPublished event) {
    apply(
        event.getProductId(),
        product ->
            product.published(
                event.getSku(), event.getName(), event.getPrice(), event.getCurrency(), at(event)));
  }

  @HandleEvent
  @Idempotent(name = CONSUMER)
  @Transactional
  public void on(ProductPriceChanged event) {
    apply(
        event.getProductId(),
        product -> product.priceChanged(event.getNewPrice(), event.getCurrency(), at(event)));
  }

  @HandleEvent
  @Idempotent(name = CONSUMER)
  @Transactional
  public void on(ProductDiscontinued event) {
    apply(event.getProductId(), product -> product.discontinued(at(event)));
  }

  private void apply(UUID productId, Consumer<CatalogProduct> change) {
    CatalogProduct product =
        products
            .query()
            .where("productId", Operators.EQUALS, productId)
            .findOne()
            .orElseGet(() -> CatalogProduct.unknown(productId));
    change.accept(product);
    products.save(product);
  }

  private static OffsetDateTime at(Event event) {
    return event.getOccurredOn().atOffset(ZoneOffset.UTC);
  }
}
