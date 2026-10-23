package com.borjaglez.shop.catalog.application.exception;

import java.util.UUID;

import com.borjaglez.shop.kernel.error.NotFoundException;

/**
 * No product with this id belongs to the seller. Products of other sellers are reported the same
 * way, so their existence is not revealed. Code {@code product-not-found}.
 */
public final class ProductNotFound extends NotFoundException {

  public ProductNotFound(UUID productId) {
    super("product-not-found", "Unknown product " + productId);
  }
}
