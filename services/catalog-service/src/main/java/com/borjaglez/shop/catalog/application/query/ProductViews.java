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

  /**
   * A product page. {@code availability} is {@code null} for a product that is no longer on sale.
   */
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
      long version,
      Availability availability) {

    /** The same page with the stock the inventory reported. */
    public ProductDetail withAvailability(Availability availability) {
      return new ProductDetail(
          id,
          slug,
          sku,
          name,
          description,
          price,
          currency,
          status,
          seller,
          categories,
          tags,
          publishedAt,
          updatedAt,
          version,
          availability);
    }
  }

  /** How much of a product is left, as the shopper sees it. */
  public enum StockStatus {
    IN_STOCK,
    /** Few units left: the page says how many. */
    LOW_STOCK,
    OUT_OF_STOCK,
    /** The inventory did not answer in time; the page is shown without stock. */
    UNKNOWN
  }

  /**
   * Stock of a product on sale.
   *
   * @param status what the page shows
   * @param units free units; {@code null} when unknown
   */
  public record Availability(StockStatus status, Integer units) {

    /** Up to this many free units the stock counts as low. */
    public static final int LOW_STOCK_UNITS = 5;

    public static Availability of(int units) {
      StockStatus status =
          units <= 0
              ? StockStatus.OUT_OF_STOCK
              : units <= LOW_STOCK_UNITS ? StockStatus.LOW_STOCK : StockStatus.IN_STOCK;
      return new Availability(status, Math.max(units, 0));
    }

    public static Availability unknown() {
      return new Availability(StockStatus.UNKNOWN, null);
    }
  }

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
        product.getVersion() == null ? 0 : product.getVersion(),
        null);
  }

  private static SellerSummary seller(Product product) {
    var seller = product.getSeller();
    return new SellerSummary(seller.getId(), seller.getDisplayName(), seller.getCity());
  }
}
