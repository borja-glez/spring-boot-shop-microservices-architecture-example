package com.borjaglez.shop.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.catalog.application.fakes.StubCatalogReadModel;
import com.borjaglez.shop.catalog.application.port.in.CatalogFacets;
import com.borjaglez.shop.catalog.application.port.in.FacetValue;
import com.borjaglez.shop.catalog.application.port.in.GetCatalogFacetsQuery;
import com.borjaglez.shop.catalog.application.port.in.PriceRange;
import com.borjaglez.shop.kernel.query.ClientFilter;

class GetCatalogFacetsHandlerTest {

  private final StubCatalogReadModel catalog = new StubCatalogReadModel();
  private final GetCatalogFacetsHandler handler = new GetCatalogFacetsHandler(catalog);

  @Test
  void answersWithTheCountsOfTheReadSideForTheClientFilter() {
    CatalogFacets facets =
        new CatalogFacets(
            List.of(new FacetValue("cafe-e-infusiones", "Café e infusiones", 8)),
            List.of(new FacetValue("seller-ana", "Tostadores Ana", 5)),
            List.of(new FacetValue("ecologico", "ecologico", 3)),
            new PriceRange(new BigDecimal("3.20"), new BigDecimal("349.00")));
    catalog.counts(facets);
    ClientFilter filter = new ClientFilter(List.of("name:contains:cafe"), List.of(), List.of());

    assertThat(handler.handle(new GetCatalogFacetsQuery(filter))).isEqualTo(facets);
    assertThat(catalog.filters()).containsExactly(filter);
  }
}
