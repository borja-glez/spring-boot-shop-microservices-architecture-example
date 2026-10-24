package com.borjaglez.shop.fixture.good.billing.domain;

public interface InvoiceRepository {

  void save(InvoiceStream invoice);
}
