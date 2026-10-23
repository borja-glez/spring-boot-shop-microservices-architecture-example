package com.borjaglez.shop.catalog.domain;

import java.util.Optional;

/** [DDD · Repository] [Hexagonal · puerto de salida] Stores and loads {@link Product}s. */
public interface ProductRepository {

  Optional<Product> findById(ProductId id);

  boolean existsBySku(Sku sku);

  /**
   * Stores the product with the events it collected, which it hands over.
   *
   * <p>A product is saved once per transaction, after the change a use case asked of it. Saving
   * does not write the new version back into the product, which keeps the version it was read with:
   * saving the same instance again once it was written fails as a concurrent change would. To
   * change a product again, read it again.
   */
  void save(Product product);
}
