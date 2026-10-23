package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import com.borjaglez.specrepository.jpa.SpecificationRepository;

/** Sellers, read through the specification-repository DSL. */
public interface SellerJpaRepository extends SpecificationRepository<SellerEntity, String> {}
