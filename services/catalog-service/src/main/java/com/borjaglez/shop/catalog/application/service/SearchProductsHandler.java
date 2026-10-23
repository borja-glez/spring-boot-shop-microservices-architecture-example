package com.borjaglez.shop.catalog.application.service;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.catalog.application.port.in.ProductCard;
import com.borjaglez.shop.catalog.application.port.in.SearchProductsQuery;
import com.borjaglez.shop.catalog.application.port.out.CatalogReadModel;
import com.borjaglez.shop.kernel.query.Page;

/**
 * [DDD · Application Service] Use case "search the catalog": a page of the products on sale that
 * match the shopper's filters.
 */
@QueryHandler(remote = false)
public class SearchProductsHandler {

  private final CatalogReadModel catalog;

  public SearchProductsHandler(CatalogReadModel catalog) {
    this.catalog = catalog;
  }

  @HandleQuery
  public Page<ProductCard> handle(SearchProductsQuery query) {
    return catalog.search(query.getFilter(), query.getPage());
  }
}
