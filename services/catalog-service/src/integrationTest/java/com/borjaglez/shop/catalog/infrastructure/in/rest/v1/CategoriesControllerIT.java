package com.borjaglez.shop.catalog.infrastructure.in.rest.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.catalog.application.port.in.CategoryView;
import com.borjaglez.shop.catalog.application.port.in.ListCategoriesQuery;
import com.borjaglez.shop.testsupport.contract.OpenApiContract;

/** The categories of the catalog API over HTTP, checked against {@code catalog-api.v1.yaml}. */
@WebMvcTest(CategoriesController.class)
class CategoriesControllerIT {

  private static final OpenApiContract CONTRACT =
      OpenApiContract.of(Path.of("src/main/openapi/catalog-api.v1.yaml"));

  @Autowired MockMvc mvc;
  @MockitoBean QueryBus queries;

  @Test
  void listsTheCategoriesWithTheirProductsOnSale() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(
            List.of(
                new CategoryView("cafe-e-infusiones", "Café e infusiones", 8),
                new CategoryView("vinos", "Vinos", 0)));

    mvc.perform(get("/api/v1/catalog/categories").header("X-Correlation-Id", "it-42"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches())
        .andExpect(header().string("X-Correlation-Id", "it-42"))
        .andExpect(
            content()
                .json(
                    """
                    [{"slug": "cafe-e-infusiones", "name": "Café e infusiones", "activeProducts": 8},
                     {"slug": "vinos", "name": "Vinos", "activeProducts": 0}]
                    """,
                    JsonCompareMode.STRICT));

    ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
    verify(queries).ask(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(ListCategoriesQuery.class);
  }

  @Test
  void aCatalogWithoutCategoriesListsNone() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(List.of());

    mvc.perform(get("/api/v1/catalog/categories"))
        .andExpect(status().isOk())
        .andExpect(CONTRACT.matches())
        .andExpect(content().json("[]", JsonCompareMode.STRICT));
  }
}
