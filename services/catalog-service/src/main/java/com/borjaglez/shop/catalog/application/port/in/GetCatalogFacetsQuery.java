package com.borjaglez.shop.catalog.application.port.in;

import java.util.Objects;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.shop.kernel.query.ClientFilter;

/**
 * [Hexagonal · puerto de entrada] Refinement counts for the products a public search matches.
 * Answered with {@link CatalogFacets}. It takes the filters of {@link SearchProductsQuery}, and no
 * sort.
 */
public final class GetCatalogFacetsQuery extends Query {

  private final ClientFilter filter;

  public GetCatalogFacetsQuery(ClientFilter filter) {
    this.filter = Objects.requireNonNull(filter, "filter must not be null");
  }

  public ClientFilter getFilter() {
    return filter;
  }
}
