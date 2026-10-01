package com.borjaglez.shop.orders.application.query;

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
   *     the inventory did not answer
   */
  Optional<Map<UUID, Integer>> available(List<UUID> productIds);
}
