package com.borjaglez.shop.orders.domain;

import java.util.UUID;

import com.borjaglez.specrepository.jpa.SpecificationRepository;

/** Local catalog projection, read through the specification-repository DSL. */
public interface CatalogProductRepository extends SpecificationRepository<CatalogProduct, UUID> {}
