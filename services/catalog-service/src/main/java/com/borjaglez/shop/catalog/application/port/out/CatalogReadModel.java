package com.borjaglez.shop.catalog.application.port.out;

import java.util.List;
import java.util.Optional;

import com.borjaglez.shop.catalog.application.port.in.CatalogFacets;
import com.borjaglez.shop.catalog.application.port.in.CategoryView;
import com.borjaglez.shop.catalog.application.port.in.ProductCard;
import com.borjaglez.shop.catalog.application.port.in.ProductDetail;
import com.borjaglez.shop.kernel.query.ClientFilter;
import com.borjaglez.shop.kernel.query.Page;
import com.borjaglez.shop.kernel.query.PageRequest;

/**
 * [Hexagonal · puerto de salida] The public read side of the catalog: views of the products on
 * sale, not aggregates. Reading changes nothing, so its adapter reads however suits it best.
 *
 * <p>The filters arrive as the shopper sent them. The adapter rejects a filter or sort outside the
 * public whitelist, above the limits of the filter syntax or with an operator that is not allowed,
 * before it runs anything; the error names the field when it is about one.
 */
public interface CatalogReadModel {

  /** The products on sale that match the filter, sorted as asked or the newest first. */
  Page<ProductCard> search(ClientFilter filter, PageRequest page);

  /**
   * Refinement counts for the products on sale that match the filter. Each facet ignores the filter
   * on its own field, so a shopper can pick several values of it.
   */
  CatalogFacets facets(ClientFilter filter);

  /**
   * The page of a published or discontinued product, without its stock: drafts have no public page.
   */
  Optional<ProductDetail> publicProduct(String slug);

  /** Every category with its number of products on sale, by name. */
  List<CategoryView> categories();
}
