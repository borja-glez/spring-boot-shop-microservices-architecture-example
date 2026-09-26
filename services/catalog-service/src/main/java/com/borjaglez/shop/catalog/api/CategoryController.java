package com.borjaglez.shop.catalog.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.catalog.application.query.ListCategoriesQuery;
import com.borjaglez.shop.catalog.application.query.ProductViews.CategoryView;

/** Browsing categories with the number of products on sale in each. */
@RestController
@RequestMapping("/api/catalog/categories")
class CategoryController {

  private final QueryBus queries;

  CategoryController(QueryBus queries) {
    this.queries = queries;
  }

  @GetMapping
  List<CategoryView> categories() {
    return queries.ask(new ListCategoriesQuery());
  }
}
