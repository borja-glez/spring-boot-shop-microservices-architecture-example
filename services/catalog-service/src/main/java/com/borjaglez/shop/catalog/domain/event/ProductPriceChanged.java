package com.borjaglez.shop.catalog.domain.event;

import java.time.Instant;

import com.borjaglez.shop.catalog.domain.Money;
import com.borjaglez.shop.catalog.domain.ProductId;

/** [DDD · Domain Event] The price of a published product changed. */
public record ProductPriceChanged(ProductId id, Money oldPrice, Money newPrice, Instant occurredOn)
    implements ProductEvent {}
