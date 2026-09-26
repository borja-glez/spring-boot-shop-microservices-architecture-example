package com.borjaglez.shop.catalog.application.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.borjaglez.shop.catalog.domain.Category;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.ProductStatus;

/** Read models returned by the catalog queries. */
public final class ProductViews {

  private ProductViews() {}

  /** A product in a search result. */
  public record ProductCard(
      UUID id,
      String slug,
      String sku,
      String name,
      BigDecimal price,
      String currency,
      ProductStatus status,
      SellerSummary seller,
      List<String> categories,
      List<String> tags,
      OffsetDateTime publishedAt) {}

  /** A product page. */
  public record ProductDetail(
      UUID id,
      String slug,
      String sku,
      String name,
      String description,
      BigDecimal price,
      String currency,
      ProductStatus status,
      SellerSummary seller,
      List<CategoryRef> categories,
      List<String> tags,
      OffsetDateTime publishedAt,
      OffsetDateTime updatedAt,
      long version) {}

  /** Public data about a seller. The email is private and never exposed. */
  public record SellerSummary(String id, String displayName, String city) {}

  /** A category reference. */
  public record CategoryRef(String slug, String name) {}

  /** A category with the number of products on sale in it. */
  public record CategoryView(String slug, String name, long activeProducts) {}

  /** Counts that let shoppers refine a search. */
  public record CatalogFacets(
      List<FacetValue> categories,
      List<FacetValue> sellers,
      List<FacetValue> tags,
      PriceRange price) {}

  /** One refinement option and how many products match it. */
  public record FacetValue(String value, String label, long count) {}

  /** Cheapest and most expensive price among the matching products. */
  public record PriceRange(BigDecimal min, BigDecimal max) {}

  static ProductCard card(Product product) {
    return new ProductCard(
        product.getId(),
        product.getSlug(),
        product.getSku(),
        product.getName(),
        product.getPrice().amount(),
        product.getPrice().currency(),
        product.getStatus(),
        seller(product),
        product.getCategories().stream().map(Category::getSlug).sorted().toList(),
        product.getTags().stream().sorted().toList(),
        product.getPublishedAt());
  }

  static ProductDetail detail(Product product) {
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
            .map(c -> new CategoryRef(c.getSlug(), c.getName()))
            .sorted(Comparator.comparing(CategoryRef::slug))
            .toList(),
        product.getTags().stream().sorted().toList(),
        product.getPublishedAt(),
        product.getUpdatedAt(),
        product.getVersion() == null ? 0 : product.getVersion());
  }

  private static SellerSummary seller(Product product) {
    var seller = product.getSeller();
    return new SellerSummary(seller.getId(), seller.getDisplayName(), seller.getCity());
  }
}
