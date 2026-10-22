package com.borjaglez.shop.fixture.good.billing.domain;

import com.borjaglez.shop.kernel.domain.DomainEvent;

public sealed interface InvoiceEvent extends DomainEvent permits InvoiceIssued {}
