package com.borjaglez.shop.catalog.application.service;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.catalog.application.port.in.CatalogFacets;
import com.borjaglez.shop.catalog.application.port.in.GetCatalogFacetsQuery;
import com.borjaglez.shop.catalog.application.port.out.CatalogReadModel;

/**
 * [DDD · Application Service] Use case "refine a search": how many products each category, seller
 * and tag would add, and the range of prices.
 */
@QueryHandler(remote = false)
public class GetCatalogFacetsHandler {

  private final CatalogReadModel catalog;

  public GetCatalogFacetsHandler(CatalogReadModel catalog) {
    this.catalog = catalog;
  }

  @HandleQuery
  public CatalogFacets handle(GetCatalogFacetsQuery query) {
    return catalog.facets(query.getFilter());
  }
}
