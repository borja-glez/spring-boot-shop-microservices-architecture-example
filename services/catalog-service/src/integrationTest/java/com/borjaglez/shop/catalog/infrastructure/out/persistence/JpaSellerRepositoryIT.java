package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.borjaglez.shop.catalog.domain.Seller;
import com.borjaglez.shop.catalog.domain.SellerId;
import com.borjaglez.shop.catalog.domain.SellerRepository;

/** Sellers of the seed (V2__catalog_seed.sql), looked up by id. */
@CatalogPersistenceTest
class JpaSellerRepositoryIT {

  @Autowired SellerRepository sellers;

  @Test
  void findsASellerWithItsPublicData() {
    SellerId ana = new SellerId("seller-ana");

    assertThat(sellers.findById(ana)).contains(new Seller(ana, "Tostadores Ana", "Madrid"));
  }

  @Test
  void anUnknownSellerIsNotFound() {
    assertThat(sellers.findById(new SellerId("seller-nobody"))).isEmpty();
  }
}
