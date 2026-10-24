package com.borjaglez.shop.catalog.domain.event;

import com.borjaglez.shop.kernel.domain.DomainEvent;

/** [DDD · Domain Event] Facts a {@code Product} announces. */
public sealed interface ProductEvent extends DomainEvent
    permits ProductPublished, ProductPriceChanged, ProductDiscontinued {}
