package com.borjaglez.shop.catalog.domain.event;

import java.time.Instant;

import com.borjaglez.shop.catalog.domain.Money;
import com.borjaglez.shop.catalog.domain.ProductId;
import com.borjaglez.shop.catalog.domain.SellerId;
import com.borjaglez.shop.catalog.domain.Sku;

/** [DDD · Domain Event] The product was put on sale. */
public record ProductPublished(
    ProductId id, Sku sku, String name, Money price, SellerId seller, Instant occurredOn)
    implements ProductEvent {}
