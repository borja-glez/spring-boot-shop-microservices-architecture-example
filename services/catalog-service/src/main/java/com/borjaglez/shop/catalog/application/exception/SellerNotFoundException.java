package com.borjaglez.shop.catalog.application.exception;

import com.borjaglez.shop.kernel.error.NotFoundException;

/** The user who sends the request is not a seller of the catalog. Code {@code seller-not-found}. */
public final class SellerNotFoundException extends NotFoundException {

  public SellerNotFoundException(String sellerId) {
    super("seller-not-found", "Unknown seller " + sellerId);
  }
}
