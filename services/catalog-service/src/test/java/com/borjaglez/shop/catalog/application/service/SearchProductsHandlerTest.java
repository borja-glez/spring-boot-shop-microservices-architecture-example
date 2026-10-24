package com.borjaglez.shop.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.catalog.application.fakes.StubCatalogReadModel;
import com.borjaglez.shop.catalog.application.port.in.ProductCard;
import com.borjaglez.shop.catalog.application.port.in.SearchProductsQuery;
import com.borjaglez.shop.catalog.application.port.in.SellerSummary;
import com.borjaglez.shop.catalog.domain.ProductStatus;
import com.borjaglez.shop.kernel.query.ClientFilter;
import com.borjaglez.shop.kernel.query.Page;
import com.borjaglez.shop.kernel.query.PageRequest;

class SearchProductsHandlerTest {

  private final StubCatalogReadModel catalog = new StubCatalogReadModel();
  private final SearchProductsHandler handler = new SearchProductsHandler(catalog);

  private static ProductCard card(String sku) {
    return new ProductCard(
        UUID.randomUUID(),
        "producto-" + sku.toLowerCase(Locale.ROOT),
        sku,
        "Producto " + sku,
        new BigDecimal("9.90"),
        "EUR",
        ProductStatus.ACTIVE,
        new SellerSummary("seller-ana", "Tostadores Ana", "Madrid"),
        List.of("cafe-e-infusiones"),
        List.of(),
        OffsetDateTime.parse("2026-09-01T10:00:00Z"));
  }

  @Test
  void answersWithThePageTheReadSideFinds() {
    List<ProductCard> cards = List.of(card("CAF-001"), card("CAF-002"));
    catalog.finds(cards, 12);

    Page<ProductCard> page =
        handler.handle(new SearchProductsQuery(ClientFilter.none(), new PageRequest(1, 2)));

    assertThat(page).isEqualTo(new Page<>(cards, 1, 2, 12));
  }

  @Test
  void passesTheClientFilterAndThePageAsTheyCame() {
    ClientFilter filter =
        new ClientFilter(
            List.of("name:contains:café"),
            List.of("seller.city:eq:Madrid;tags:eq:ecologico"),
            List.of("price.amount,desc"));

    handler.handle(new SearchProductsQuery(filter, new PageRequest(3, 20)));

    assertThat(catalog.filters()).containsExactly(filter);
    assertThat(catalog.pages()).containsExactly(new PageRequest(3, 20));
  }
}
