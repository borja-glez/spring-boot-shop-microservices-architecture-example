package com.borjaglez.shop.catalog.infrastructure.in.rest.v1;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.catalog.application.port.in.CategoryView;
import com.borjaglez.shop.catalog.application.port.in.ListCategoriesQuery;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.api.CategoriesApi;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.Category;

/**
 * [Hexagonal · adaptador driving] The categories of the catalog API, {@code
 * /api/v1/catalog/categories}: the operation tagged {@code categories} in {@code
 * catalog-api.v1.yaml}, each category with the number of products on sale in it.
 */
@RestController
class CategoriesController implements CategoriesApi {

  private final QueryBus queries;

  CategoriesController(QueryBus queries) {
    this.queries = queries;
  }

  @Override
  public ResponseEntity<List<Category>> listCategories() {
    List<CategoryView> categories = queries.ask(new ListCategoriesQuery());
    return ResponseEntity.ok(CatalogRestMapper.categories(categories));
  }
}
