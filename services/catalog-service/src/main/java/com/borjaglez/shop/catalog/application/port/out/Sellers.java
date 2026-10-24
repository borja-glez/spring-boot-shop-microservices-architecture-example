package com.borjaglez.shop.catalog.application.port.out;

import java.util.Optional;

import com.borjaglez.shop.catalog.domain.SellerId;

/**
 * [Hexagonal · puerto de salida] Looks up the {@link Seller}s of the catalog: reference data the
 * use cases read, not an aggregate they change.
 */
public interface Sellers {

  Optional<Seller> findById(SellerId id);
}
