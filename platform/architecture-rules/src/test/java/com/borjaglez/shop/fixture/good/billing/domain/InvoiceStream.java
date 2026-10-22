package com.borjaglez.shop.fixture.good.billing.domain;

import com.borjaglez.shop.kernel.domain.EventSourcedAggregate;

public class InvoiceStream extends EventSourcedAggregate<InvoiceEvent> {

  private String number;

  @Override
  public String id() {
    return number;
  }

  @Override
  protected void when(InvoiceEvent event) {
    if (event instanceof InvoiceIssued issued) {
      number = issued.number();
    }
  }
}
