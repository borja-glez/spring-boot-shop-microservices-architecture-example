package com.borjaglez.shop.fixture.good.orders.domain;

import java.time.Instant;

public record OrderPlaced(String orderId, Instant occurredOn) implements OrderEvent {}
