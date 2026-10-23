package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import java.util.UUID;

import com.borjaglez.specrepository.jpa.SpecificationRepository;

/** Categories, read through the specification-repository DSL. */
public interface CategoryJpaRepository extends SpecificationRepository<CategoryEntity, UUID> {}
