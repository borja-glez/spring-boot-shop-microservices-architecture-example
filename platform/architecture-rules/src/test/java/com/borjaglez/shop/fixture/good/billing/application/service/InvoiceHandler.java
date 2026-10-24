package com.borjaglez.shop.fixture.good.billing.application.service;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.shop.fixture.good.billing.domain.Invoice;

@CommandHandler
public class InvoiceHandler {

  public Invoice issue(String number) {
    return new Invoice(number);
  }
}
