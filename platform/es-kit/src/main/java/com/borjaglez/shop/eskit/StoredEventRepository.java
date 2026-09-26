package com.borjaglez.shop.eskit;

import com.borjaglez.specrepository.jpa.SpecificationRepository;

/** Reads of the event store go through the specification-repository DSL. */
public interface StoredEventRepository extends SpecificationRepository<StoredEvent, Long> {}
