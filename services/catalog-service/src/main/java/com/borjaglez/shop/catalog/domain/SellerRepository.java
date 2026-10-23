package com.borjaglez.shop.catalog.domain;

import java.util.Optional;

/** [DDD · Repository] [Hexagonal · puerto de salida] Looks up {@link Seller}s. */
public interface SellerRepository {

  Optional<Seller> findById(SellerId id);
}
