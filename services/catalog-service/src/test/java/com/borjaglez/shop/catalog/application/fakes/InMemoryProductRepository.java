package com.borjaglez.shop.catalog.application.fakes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.Product.ProductState;
import com.borjaglez.shop.catalog.domain.ProductId;
import com.borjaglez.shop.catalog.domain.ProductRepository;
import com.borjaglez.shop.catalog.domain.Sku;
import com.borjaglez.shop.catalog.domain.event.ProductEvent;

/**
 * [Hexagonal · adaptador driven] The products in a map, for the unit tests of the use cases.
 *
 * <p>It keeps copies of the state and hands out new aggregates, as a database would: a change a use
 * case makes without saving it is lost, and the test notices. Every save bumps the version and
 * keeps the events the product collected, in the order they were saved.
 */
public final class InMemoryProductRepository implements ProductRepository {

  private final Map<ProductId, ProductState> products = new LinkedHashMap<>();
  private final List<ProductEvent> events = new ArrayList<>();
  private int saves;

  @Override
  public Optional<Product> findById(ProductId id) {
    return Optional.ofNullable(products.get(id)).map(Product::reconstitute);
  }

  @Override
  public boolean existsBySku(Sku sku) {
    return products.values().stream().anyMatch(state -> state.sku().equals(sku));
  }

  @Override
  public void save(Product product) {
    store(product.state());
    events.addAll(product.pullEvents());
    saves++;
  }

  /** Stores a product as if it had been saved earlier, forgetting the events it collected. */
  public void given(Product product) {
    store(product.state());
    product.pullEvents();
  }

  /** The stored state of a product. */
  public ProductState stored(ProductId id) {
    return Optional.ofNullable(products.get(id)).orElseThrow();
  }

  /** Every product stored. */
  public List<ProductState> all() {
    return List.copyOf(products.values());
  }

  /** The events of every save, in order. */
  public List<ProductEvent> events() {
    return List.copyOf(events);
  }

  /** How many times a use case saved a product. */
  public int saves() {
    return saves;
  }

  private void store(ProductState state) {
    long version = state.version() == null ? 0 : state.version() + 1;
    products.put(
        state.id(),
        new ProductState(
            state.id(),
            state.sku(),
            state.slug(),
            state.name(),
            state.description(),
            state.price(),
            state.status(),
            state.seller(),
            state.categories(),
            state.tags(),
            state.createdAt(),
            state.publishedAt(),
            state.updatedAt(),
            version));
  }
}
