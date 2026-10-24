package com.borjaglez.shop.catalog.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.borjaglez.shop.catalog.domain.ProductId;

/**
 * [Hexagonal · puerto de salida] Free stock per product, as the inventory knows it right now. Stock
 * changes with every checkout, so the catalog asks for it instead of keeping a copy.
 */
public interface StockLevels {

  /**
   * Asks the inventory for the free units of some products.
   *
   * @return free units per product, without the products the inventory does not know; empty when
   *     the inventory did not answer in time, so the caller can show the page without them
   */
  Optional<Map<ProductId, Integer>> available(List<ProductId> products);
}
