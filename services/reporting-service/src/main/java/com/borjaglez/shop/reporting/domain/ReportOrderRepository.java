package com.borjaglez.shop.reporting.domain;

import java.util.UUID;

import com.borjaglez.specrepository.jpa.SpecificationRepository;

public interface ReportOrderRepository extends SpecificationRepository<ReportOrder, UUID> {}
