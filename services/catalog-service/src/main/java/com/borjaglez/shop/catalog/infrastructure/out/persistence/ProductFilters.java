package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import java.util.List;
import java.util.Set;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;

/**
 * The public product filters, the one definition the search and its facets share, so both accept
 * exactly the same requests.
 *
 * <p>Private data such as the seller's email is never listed. Text searches on the name and the
 * description ignore case and accents ({@link #CASE_INSENSITIVE_FIELDS}), so a shopper who types
 * "cafe" finds "Café de Colombia". The search may be sorted by {@link #SORTABLE_FIELDS}; the facets
 * are not sorted.
 *
 * <p>The contract of the API, {@code catalog-api.v1.yaml}, lists the same fields in the {@code
 * x-specrepo-filterable-fields} and {@code x-specrepo-sortable-fields} of the search and the facets
 * and in the patterns of their filter parameters.
 */
public final class ProductFilters {

  /** The fields a shopper may filter by, in {@code filter} and {@code orFilter}. */
  public static final List<String> FILTERABLE_FIELDS =
      List.of(
          "name",
          "description",
          "sku",
          "price.amount",
          "status",
          "categories.slug",
          "tags",
          "seller.id",
          "seller.city",
          "publishedAt");

  /** The fields a shopper may sort the search by. */
  public static final List<String> SORTABLE_FIELDS =
      List.of("name", "sku", "price.amount", "publishedAt");

  /** The fields matched ignoring case and accents. */
  public static final List<String> CASE_INSENSITIVE_FIELDS = List.of("name", "description");

  /** What the search accepts. */
  static final AllowedFieldsPolicy SEARCH =
      AllowedFieldsPolicy.of(Set.copyOf(FILTERABLE_FIELDS), Set.copyOf(SORTABLE_FIELDS));

  /** What the facets accept: the filters of the search and no sort. */
  static final AllowedFieldsPolicy FACETS =
      AllowedFieldsPolicy.of(Set.copyOf(FILTERABLE_FIELDS), Set.of());

  private ProductFilters() {}
}
