package com.borjaglez.shop.catalog.application.service;

import java.util.List;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.catalog.application.port.in.CategoryView;
import com.borjaglez.shop.catalog.application.port.in.ListCategoriesQuery;
import com.borjaglez.shop.catalog.application.port.out.CatalogReadModel;

/**
 * [DDD · Application Service] Use case "browse the categories", each with its number of products on
 * sale.
 */
@QueryHandler(remote = false)
public class ListCategoriesHandler {

  private final CatalogReadModel catalog;

  public ListCategoriesHandler(CatalogReadModel catalog) {
    this.catalog = catalog;
  }

  @HandleQuery
  public List<CategoryView> handle(ListCategoriesQuery query) {
    return catalog.categories();
  }
}
