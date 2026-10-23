package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import java.util.Optional;

import com.borjaglez.shop.catalog.application.port.out.Seller;
import com.borjaglez.shop.catalog.application.port.out.Sellers;
import com.borjaglez.shop.catalog.domain.SellerId;
import com.borjaglez.specrepository.core.Operators;

/**
 * [Hexagonal · adaptador driven] Looks sellers up in PostgreSQL. Only their public data reaches the
 * core; the email stays here.
 */
public class JpaSellers implements Sellers {

  private final SellerJpaRepository sellers;

  public JpaSellers(SellerJpaRepository sellers) {
    this.sellers = sellers;
  }

  @Override
  public Optional<Seller> findById(SellerId id) {
    return sellers
        .query()
        .where("id", Operators.EQUALS, id.value())
        .findOne()
        .map(
            seller ->
                new Seller(
                    new SellerId(seller.getId()), seller.getDisplayName(), seller.getCity()));
  }
}
