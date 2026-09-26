package com.borjaglez.shop.notifications.domain;

import java.util.UUID;

import com.borjaglez.specrepository.jpa.SpecificationRepository;

public interface OrderOwnerRepository extends SpecificationRepository<OrderOwner, UUID> {}
