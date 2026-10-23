package com.borjaglez.shop.catalog.application.fakes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.borjaglez.shop.catalog.domain.Seller;
import com.borjaglez.shop.catalog.domain.SellerId;
import com.borjaglez.shop.catalog.domain.SellerRepository;

/** [Hexagonal · adaptador driven] The sellers in a map, for the unit tests of the use cases. */
public final class InMemorySellers implements SellerRepository {

  private final Map<SellerId, Seller> sellers = new LinkedHashMap<>();

  @Override
  public Optional<Seller> findById(SellerId id) {
    return Optional.ofNullable(sellers.get(id));
  }

  /** Registers a seller and returns its id. */
  public SellerId add(String id, String displayName, String city) {
    SellerId sellerId = new SellerId(id);
    sellers.put(sellerId, new Seller(sellerId, displayName, city));
    return sellerId;
  }
}
