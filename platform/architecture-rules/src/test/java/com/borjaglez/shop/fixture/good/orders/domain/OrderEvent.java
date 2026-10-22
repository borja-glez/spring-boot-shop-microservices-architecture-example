package com.borjaglez.shop.fixture.good.orders.domain;

import com.borjaglez.shop.kernel.domain.DomainEvent;

public sealed interface OrderEvent extends DomainEvent permits OrderPlaced {}
