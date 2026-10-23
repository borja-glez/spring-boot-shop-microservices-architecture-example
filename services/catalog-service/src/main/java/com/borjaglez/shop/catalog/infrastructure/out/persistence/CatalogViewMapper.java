package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import java.util.Comparator;

import com.borjaglez.shop.catalog.application.port.in.CategoryRef;
import com.borjaglez.shop.catalog.application.port.in.ProductCard;
import com.borjaglez.shop.catalog.application.port.in.ProductDetail;
import com.borjaglez.shop.catalog.application.port.in.SellerSummary;

/**
 * Builds the read views of the catalog from its stored form, straight from the rows, without
 * rebuilding the aggregate. Instants keep the UTC offset they are read with.
 */
final class CatalogViewMapper {

  private CatalogViewMapper() {}

  static ProductCard card(ProductEntity product) {
    return new ProductCard(
        product.getId(),
        product.getSlug(),
        product.getSku(),
        product.getName(),
        product.getPrice().amount(),
        product.getPrice().currency(),
        product.getStatus(),
        seller(product),
        product.getCategories().stream().map(CategoryEntity::getSlug).sorted().toList(),
        product.getTags().stream().sorted().toList(),
        product.getPublishedAt());
  }

  /** The page of a product, without its stock. */
  static ProductDetail detail(ProductEntity product) {
    return new ProductDetail(
        product.getId(),
        product.getSlug(),
        product.getSku(),
        product.getName(),
        product.getDescription(),
        product.getPrice().amount(),
        product.getPrice().currency(),
        product.getStatus(),
        seller(product),
        product.getCategories().stream()
            .map(category -> new CategoryRef(category.getSlug(), category.getName()))
            .sorted(Comparator.comparing(CategoryRef::slug))
            .toList(),
        product.getTags().stream().sorted().toList(),
        product.getPublishedAt(),
        product.getUpdatedAt(),
        product.getVersion(),
        null);
  }

  private static SellerSummary seller(ProductEntity product) {
    SellerEntity seller = product.getSeller();
    return new SellerSummary(seller.getId(), seller.getDisplayName(), seller.getCity());
  }
}
