package com.borjaglez.shop.catalog.domain;

import java.util.Optional;

/** [DDD · Repository] [Hexagonal · puerto de salida] Stores and loads {@link Product}s. */
public interface ProductRepository {

  Optional<Product> findById(ProductId id);

  boolean existsBySku(Sku sku);

  void save(Product product);
}
