package com.borjaglez.shop.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.borjaglez.shop.catalog.application.exception.ProductNotFoundException;
import com.borjaglez.shop.catalog.application.fakes.StubCatalogReadModel;
import com.borjaglez.shop.catalog.application.fakes.StubStockLevels;
import com.borjaglez.shop.catalog.application.port.in.Availability;
import com.borjaglez.shop.catalog.application.port.in.CategoryRef;
import com.borjaglez.shop.catalog.application.port.in.GetProductQuery;
import com.borjaglez.shop.catalog.application.port.in.ProductDetail;
import com.borjaglez.shop.catalog.application.port.in.SellerSummary;
import com.borjaglez.shop.catalog.application.port.in.StockStatus;
import com.borjaglez.shop.catalog.domain.ProductId;
import com.borjaglez.shop.catalog.domain.ProductStatus;
import com.borjaglez.shop.kernel.error.NotFoundException;

class GetProductHandlerTest {

  private static final String SLUG = "cafe-de-colombia-en-grano-1-kg-caf-001";
  private static final UUID ID = UUID.fromString("8a9c2c1e-6f59-4a44-9d4b-0f6f4e3f1c11");

  private final StubCatalogReadModel catalog = new StubCatalogReadModel();
  private final StubStockLevels inventory = new StubStockLevels();
  private final GetProductHandler handler = new GetProductHandler(catalog, inventory);

  private static ProductDetail page(ProductStatus status) {
    return new ProductDetail(
        ID,
        SLUG,
        "CAF-001",
        "Café de Colombia en grano 1 kg",
        "Tueste medio, notas de caramelo y cítricos.",
        new BigDecimal("18.90"),
        "EUR",
        status,
        new SellerSummary("seller-ana", "Tostadores Ana", "Madrid"),
        List.of(new CategoryRef("cafe-e-infusiones", "Café e infusiones")),
        List.of("comercio-justo", "ecologico"),
        OffsetDateTime.parse("2026-09-01T10:00:00Z"),
        OffsetDateTime.parse("2026-09-02T08:30:00Z"),
        2,
        null);
  }

  private ProductDetail ask() {
    return handler.handle(new GetProductQuery(SLUG));
  }

  @ParameterizedTest(name = "{0} free units: {1}")
  @CsvSource({
    "0, OUT_OF_STOCK, 0",
    "1, LOW_STOCK, 1",
    "5, LOW_STOCK, 5",
    "6, IN_STOCK, 6",
    "120, IN_STOCK, 120",
    "-2, OUT_OF_STOCK, 0"
  })
  void aProductOnSaleShowsTheStockTheInventoryReports(
      int available, StockStatus status, int shown) {
    catalog.given(page(ProductStatus.ACTIVE));
    inventory.stock(new ProductId(ID), available);

    assertThat(ask().availability()).isEqualTo(new Availability(status, shown));
  }

  @Test
  void theRestOfThePageIsWhatTheReadSideFound() {
    catalog.given(page(ProductStatus.ACTIVE));
    inventory.stock(new ProductId(ID), 3);

    assertThat(ask())
        .isEqualTo(
            page(ProductStatus.ACTIVE)
                .withAvailability(new Availability(StockStatus.LOW_STOCK, 3)));
  }

  @Test
  void theInventoryIsAskedAboutThatProductOnly() {
    catalog.given(page(ProductStatus.ACTIVE));

    ask();

    assertThat(inventory.asked()).containsExactly(List.of(new ProductId(ID)));
  }

  @Test
  void aProductTheInventoryDoesNotKnowYetIsOutOfStock() {
    catalog.given(page(ProductStatus.ACTIVE));

    assertThat(ask().availability()).isEqualTo(new Availability(StockStatus.OUT_OF_STOCK, 0));
  }

  @Test
  void thePageIsShownWithoutStockWhenTheInventoryDoesNotAnswer() {
    catalog.given(page(ProductStatus.ACTIVE));
    inventory.silent();

    ProductDetail detail = ask();

    assertThat(detail.availability()).isEqualTo(new Availability(StockStatus.UNKNOWN, null));
    assertThat(detail.withAvailability(null)).isEqualTo(page(ProductStatus.ACTIVE));
  }

  @ParameterizedTest
  @EnumSource(value = ProductStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
  void aProductNotOnSaleHasNoStockAndTheInventoryIsNotAsked(ProductStatus status) {
    catalog.given(page(status));

    assertThat(ask()).isEqualTo(page(status));
    assertThat(inventory.asked()).isEmpty();
  }

  @Test
  void aProductWithoutAPublicPageIsNotFound() {
    assertThatThrownBy(this::ask)
        .isInstanceOf(ProductNotFoundException.class)
        .isInstanceOf(NotFoundException.class)
        .hasMessage("No product is published as " + SLUG)
        .extracting(e -> ((NotFoundException) e).code())
        .isEqualTo("product-not-found");
    assertThat(inventory.asked()).isEmpty();
  }
}
