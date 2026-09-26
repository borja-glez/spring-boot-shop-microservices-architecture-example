package com.borjaglez.shop.inventory.domain;

import java.util.UUID;

import com.borjaglez.specrepository.jpa.SpecificationRepository;

public interface ReservationRepository extends SpecificationRepository<Reservation, UUID> {}
