package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.borjaglez.shop.catalog.application.port.out.Seller;
import com.borjaglez.shop.catalog.application.port.out.Sellers;
import com.borjaglez.shop.catalog.domain.SellerId;

/** Sellers of the seed (V2__catalog_seed.sql), looked up by id. */
@CatalogPersistenceTest
class JpaSellersIT {

  @Autowired Sellers sellers;

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
