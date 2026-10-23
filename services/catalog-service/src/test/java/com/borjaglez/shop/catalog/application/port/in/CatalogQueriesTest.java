package com.borjaglez.shop.catalog.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.kernel.query.ClientFilter;
import com.borjaglez.shop.kernel.query.PageRequest;

class CatalogQueriesTest {

  private static final ClientFilter FILTER =
      new ClientFilter(List.of("name:contains:cafe"), List.of(), List.of("name"));

  @Test
  void aSearchKeepsItsFilterAndPage() {
    SearchProductsQuery query = new SearchProductsQuery(FILTER, new PageRequest(2, 5));

    assertThat(query.getFilter()).isEqualTo(FILTER);
    assertThat(query.getPage()).isEqualTo(new PageRequest(2, 5));
  }

  @Test
  void aSearchNeedsAFilterAndAPage() {
    assertThatNullPointerException()
        .isThrownBy(() -> new SearchProductsQuery(null, new PageRequest(0, 20)))
        .withMessage("filter must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> new SearchProductsQuery(FILTER, null))
        .withMessage("page must not be null");
  }

  @Test
  void facetsKeepTheirFilter() {
    assertThat(new GetCatalogFacetsQuery(FILTER).getFilter()).isEqualTo(FILTER);
    assertThatNullPointerException()
        .isThrownBy(() -> new GetCatalogFacetsQuery(null))
        .withMessage("filter must not be null");
  }

  @Test
  void aProductPageIsAskedBySlug() {
    assertThat(new GetProductQuery("cafe-caf-001").getSlug()).isEqualTo("cafe-caf-001");
    assertThatNullPointerException()
        .isThrownBy(() -> new GetProductQuery(null))
        .withMessage("slug must not be null");
  }

  @Test
  void everyQueryIsANewMessage() {
    assertThat(new ListCategoriesQuery().getQueryId())
        .isNotEqualTo(new ListCategoriesQuery().getQueryId());
  }
}
