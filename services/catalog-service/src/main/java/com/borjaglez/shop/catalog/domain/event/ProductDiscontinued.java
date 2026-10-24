package com.borjaglez.shop.catalog.domain.event;

import java.time.Instant;

import com.borjaglez.shop.catalog.domain.ProductId;

/** [DDD · Domain Event] A published product was withdrawn for good. */
public record ProductDiscontinued(ProductId id, String reason, Instant occurredOn)
    implements ProductEvent {}
