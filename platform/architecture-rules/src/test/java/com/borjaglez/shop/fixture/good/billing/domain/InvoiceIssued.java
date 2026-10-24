package com.borjaglez.shop.fixture.good.billing.domain;

import java.time.Instant;

public record InvoiceIssued(String number, Instant occurredOn) implements InvoiceEvent {}
