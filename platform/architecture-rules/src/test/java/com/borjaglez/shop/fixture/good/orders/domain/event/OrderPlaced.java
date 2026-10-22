package com.borjaglez.shop.fixture.good.orders.domain.event;

import java.time.Instant;

public record OrderPlaced(String orderId, Instant occurredOn) implements OrderEvent {}
