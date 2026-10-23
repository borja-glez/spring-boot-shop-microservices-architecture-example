package com.borjaglez.shop.catalog.application.service;

import java.util.UUID;

import com.borjaglez.shop.catalog.application.exception.ProductNotFound;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.ProductId;
import com.borjaglez.shop.catalog.domain.ProductRepository;
import com.borjaglez.shop.catalog.domain.SellerId;

/** Finds the product a seller wants to change, among the products of that seller only. */
final class OwnedProducts {

  private OwnedProducts() {}

  /**
   * The product, if it belongs to the seller.
   *
   * @throws ProductNotFound if there is no such product or another seller owns it, so the products
   *     of other sellers cannot be told apart from missing ones
   */
  static Product find(ProductRepository products, UUID productId, String sellerId) {
    SellerId seller = new SellerId(sellerId);
    return products
        .findById(new ProductId(productId))
        .filter(product -> product.belongsTo(seller))
        .orElseThrow(() -> new ProductNotFound(productId));
  }
}
