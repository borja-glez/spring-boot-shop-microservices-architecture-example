package com.borjaglez.shop.catalog.domain;

import java.util.UUID;

import com.borjaglez.specrepository.jpa.SpecificationRepository;

/** Categories, read through the specification-repository DSL. */
public interface CategoryRepository extends SpecificationRepository<Category, UUID> {}
