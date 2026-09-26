package com.borjaglez.shop.orders.domain;

import java.util.UUID;

import com.borjaglez.specrepository.jpa.SpecificationRepository;

public interface CheckoutSagaRepository extends SpecificationRepository<CheckoutSaga, UUID> {}
