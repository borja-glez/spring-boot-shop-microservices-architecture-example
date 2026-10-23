package com.borjaglez.shop.catalog.domain;

import com.borjaglez.specrepository.jpa.SpecificationRepository;

/** Sellers, read through the specification-repository DSL. */
public interface SellerJpaRepository extends SpecificationRepository<SellerEntity, String> {}
