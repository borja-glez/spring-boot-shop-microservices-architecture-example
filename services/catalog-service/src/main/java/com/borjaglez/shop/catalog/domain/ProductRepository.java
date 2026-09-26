package com.borjaglez.shop.catalog.domain;

import java.util.UUID;

import com.borjaglez.specrepository.jpa.SpecificationRepository;

/**
 * Products. Every read goes through the specification-repository DSL ({@link #query()}) or a {@code
 * QueryPlan}; there are deliberately no derived or {@code @Query} methods.
 */
public interface ProductRepository extends SpecificationRepository<Product, UUID> {}
