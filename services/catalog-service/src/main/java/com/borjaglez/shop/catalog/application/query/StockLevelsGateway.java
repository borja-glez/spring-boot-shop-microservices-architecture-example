package com.borjaglez.shop.catalog.application.query;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Free stock per product, as the inventory service knows it right now. */
public interface StockLevelsGateway {

  /**
   * Asks the inventory for the free units of some products.
   *
   * @return free units per product id, without the products the inventory does not know; empty when
   *     the inventory did not answer, so the caller can show the page without them
   */
  Optional<Map<UUID, Integer>> available(List<UUID> productIds);
}
