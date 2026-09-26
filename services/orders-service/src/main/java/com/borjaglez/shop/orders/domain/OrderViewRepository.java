package com.borjaglez.shop.orders.domain;

import java.util.UUID;

import com.borjaglez.specrepository.jpa.SpecificationRepository;

/** Order read model, read through the specification-repository DSL and HTTP plans. */
public interface OrderViewRepository extends SpecificationRepository<OrderView, UUID> {}
