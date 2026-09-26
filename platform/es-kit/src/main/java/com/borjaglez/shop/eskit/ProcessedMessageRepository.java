package com.borjaglez.shop.eskit;

import com.borjaglez.specrepository.jpa.SpecificationRepository;

/** Processed messages, read through the specification-repository DSL. */
public interface ProcessedMessageRepository
    extends SpecificationRepository<ProcessedMessage, ProcessedMessage.Key> {}
