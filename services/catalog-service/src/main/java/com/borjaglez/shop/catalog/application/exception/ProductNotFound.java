package com.borjaglez.shop.catalog.application.exception;

import java.util.UUID;

import com.borjaglez.shop.kernel.error.NotFoundException;

/**
 * No product with this id belongs to the seller, or no product with a public page has this slug.
 * Products of other sellers and drafts are reported the same way, so their existence is not
 * revealed. Code {@code product-not-found}.
 */
public final class ProductNotFound extends NotFoundException {

  public ProductNotFound(UUID productId) {
    this("Unknown product " + productId);
  }

  private ProductNotFound(String message) {
    super("product-not-found", message);
  }

  /** No published or discontinued product has this slug. */
  public static ProductNotFound publishedAs(String slug) {
    return new ProductNotFound("No product is published as " + slug);
  }
}
