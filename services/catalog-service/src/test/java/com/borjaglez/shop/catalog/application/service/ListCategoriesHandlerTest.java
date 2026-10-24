package com.borjaglez.shop.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.catalog.application.fakes.StubCatalogReadModel;
import com.borjaglez.shop.catalog.application.port.in.CategoryView;
import com.borjaglez.shop.catalog.application.port.in.ListCategoriesQuery;

class ListCategoriesHandlerTest {

  private final StubCatalogReadModel catalog = new StubCatalogReadModel();
  private final ListCategoriesHandler handler = new ListCategoriesHandler(catalog);

  @Test
  void answersWithTheCategoriesOfTheReadSide() {
    List<CategoryView> categories =
        List.of(
            new CategoryView("aceites-y-vinagres", "Aceites y vinagres", 4),
            new CategoryView("cafe-e-infusiones", "Café e infusiones", 8));
    catalog.categories(categories);

    assertThat(handler.handle(new ListCategoriesQuery())).isEqualTo(categories);
  }
}
