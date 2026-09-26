package com.borjaglez.shop.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.contracts.catalog.ProductDiscontinued;
import com.borjaglez.shop.contracts.catalog.ProductPriceChanged;
import com.borjaglez.shop.contracts.catalog.ProductPublished;
import com.borjaglez.shop.support.error.BusinessRuleViolationException;

class ProductTest {

  private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final Seller seller =
      new Seller("seller-ana", "Tostadores Ana", "ana@example.com", "Madrid");
  private final Category coffee = new Category(UUID.randomUUID(), "cafe-e-infusiones", "Café");

  private Product draft() {
    return Product.draft(
        new ProductDraft(
            seller,
            "caf-001",
            "Café de Colombia en grano",
            "Tueste medio, 1 kg",
            Money.of("18.90", "EUR"),
            Set.of(coffee),
            Set.of(" Ecológico ", "comercio justo")),
        clock);
  }

  @Test
  void draftIsNotPublicAndRecordsNothing() {
    Product product = draft();

    assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
    assertThat(product.getPublishedAt()).isNull();
    assertThat(product.getCreatedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    assertThat(product.pullEvents()).isEmpty();
  }

  @Test
  void draftNormalisesSkuSlugAndTags() {
    Product product = draft();

    assertThat(product.getSku()).isEqualTo("CAF-001");
    assertThat(product.getSlug()).isEqualTo("cafe-de-colombia-en-grano-caf-001");
    assertThat(product.getTags()).containsExactlyInAnyOrder("ecologico", "comercio-justo");
  }

  @Test
  void rejectsInvalidSku() {
    assertThatThrownBy(
            () ->
                Product.draft(
                    new ProductDraft(
                        seller, "a b", "Café", "", Money.of("1", "EUR"), Set.of(), Set.of()),
                    clock))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "invalid-sku");
  }

  @Test
  void rejectsBlankName() {
    assertThatThrownBy(
            () ->
                Product.draft(
                    new ProductDraft(
                        seller, "SKU-1", "  ", "", Money.of("1", "EUR"), Set.of(), Set.of()),
                    clock))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "invalid-name");
  }

  @Test
  void rejectsMoreThanTenTags() {
    Set<String> tags = Set.of("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a10", "a11");
    assertThatThrownBy(
            () ->
                Product.draft(
                    new ProductDraft(
                        seller, "SKU-1", "Café", "", Money.of("1", "EUR"), Set.of(), tags),
                    clock))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "too-many-tags");
  }

  @Test
  void publishMakesTheProductActiveAndRecordsProductPublished() {
    Product product = draft();

    product.publish(clock);

    assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(product.getPublishedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    assertThat(product.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            ProductPublished.class,
            event -> {
              assertThat(event.getProductId()).isEqualTo(product.getId());
              assertThat(event.getSku()).isEqualTo("CAF-001");
              assertThat(event.getPrice()).isEqualByComparingTo("18.90");
              assertThat(event.getCurrency()).isEqualTo("EUR");
              assertThat(event.getSellerId()).isEqualTo("seller-ana");
            });
  }

  @Test
  void publishingTwiceIsRejected() {
    Product product = draft();
    product.publish(clock);

    assertThatThrownBy(() -> product.publish(clock))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "product-already-published");
  }

  @Test
  void discontinuedProductCannotBePublished() {
    Product product = draft();
    product.discontinue("Sin proveedor", clock);

    assertThatThrownBy(() -> product.publish(clock))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "product-discontinued");
  }

  @Test
  void priceChangeOfActiveProductRecordsOldAndNewPrice() {
    Product product = draft();
    product.publish(clock);
    product.pullEvents();

    product.changePrice(Money.of("16.50", "EUR"), clock);

    assertThat(product.getPrice()).isEqualTo(Money.of("16.50", "EUR"));
    assertThat(product.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            ProductPriceChanged.class,
            event -> {
              assertThat(event.getOldPrice()).isEqualByComparingTo("18.90");
              assertThat(event.getNewPrice()).isEqualByComparingTo("16.50");
            });
  }

  @Test
  void priceChangeOfDraftRecordsNothing() {
    Product product = draft();

    product.changePrice(Money.of("20.00", "EUR"), clock);

    assertThat(product.getPrice()).isEqualTo(Money.of("20.00", "EUR"));
    assertThat(product.pullEvents()).isEmpty();
  }

  @Test
  void samePriceIsANoOp() {
    Product product = draft();
    product.publish(clock);
    product.pullEvents();

    product.changePrice(Money.of("18.9", "EUR"), clock);

    assertThat(product.pullEvents()).isEmpty();
  }

  @Test
  void priceInAnotherCurrencyIsRejected() {
    Product product = draft();

    assertThatThrownBy(() -> product.changePrice(Money.of("20", "USD"), clock))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "currency-mismatch");
  }

  @Test
  void discontinuedProductPriceCannotChange() {
    Product product = draft();
    product.discontinue("Sin proveedor", clock);

    assertThatThrownBy(() -> product.changePrice(Money.of("20", "EUR"), clock))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "product-discontinued");
  }

  @Test
  void discontinuingAPublishedProductRecordsProductDiscontinued() {
    Product product = draft();
    product.publish(clock);
    product.pullEvents();

    product.discontinue("Fin de temporada", clock);

    assertThat(product.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
    assertThat(product.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            ProductDiscontinued.class,
            event -> assertThat(event.getReason()).isEqualTo("Fin de temporada"));
  }

  @Test
  void discontinuingANeverPublishedProductRecordsNothing() {
    Product product = draft();

    product.discontinue("Descartado", clock);

    assertThat(product.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
    assertThat(product.pullEvents()).isEmpty();
  }

  @Test
  void discontinuingTwiceIsANoOp() {
    Product product = draft();
    product.publish(clock);
    product.discontinue("Fin de temporada", clock);
    product.pullEvents();

    product.discontinue("Otra vez", clock);

    assertThat(product.pullEvents()).isEmpty();
  }

  @Test
  void pullEventsDrainsTheRecordedEvents() {
    Product product = draft();
    product.publish(clock);

    assertThat(product.pullEvents()).hasSize(1);
    assertThat(product.pullEvents()).isEmpty();
  }
}
