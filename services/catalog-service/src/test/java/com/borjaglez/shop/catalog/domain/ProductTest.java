package com.borjaglez.shop.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.catalog.domain.Product.ProductState;
import com.borjaglez.shop.catalog.domain.event.ProductDiscontinued;
import com.borjaglez.shop.catalog.domain.event.ProductPriceChanged;
import com.borjaglez.shop.catalog.domain.event.ProductPublished;
import com.borjaglez.shop.catalog.domain.exception.CurrencyMismatchException;
import com.borjaglez.shop.catalog.domain.exception.InvalidDescriptionException;
import com.borjaglez.shop.catalog.domain.exception.InvalidNameException;
import com.borjaglez.shop.catalog.domain.exception.ProductAlreadyPublishedException;
import com.borjaglez.shop.catalog.domain.exception.ProductDiscontinuedException;
import com.borjaglez.shop.catalog.domain.exception.TooManyTagsException;

class ProductTest {

  private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
  private static final Instant LATER = NOW.plus(Duration.ofHours(1));
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final Clock later = Clock.fixed(LATER, ZoneOffset.UTC);
  private final SellerId seller = new SellerId("seller-ana");
  private final CategoryId coffee = new CategoryId(UUID.randomUUID());

  private ProductDraft draftOf(String name, String description, Set<Tag> tags) {
    return new ProductDraft(
        seller,
        new Sku("caf-001"),
        name,
        description,
        Money.of("18.90", "EUR"),
        Set.of(coffee),
        tags);
  }

  private Product draft() {
    return Product.draft(
        draftOf(
            "Café de Colombia en grano",
            "Tueste medio, 1 kg",
            Set.of(new Tag(" Ecológico "), new Tag("comercio justo"))),
        clock);
  }

  private static Set<Tag> tags(int count) {
    Set<Tag> tags = new LinkedHashSet<>();
    for (int i = 1; i <= count; i++) {
      tags.add(new Tag("a" + i));
    }
    return tags;
  }

  @Test
  void draftIsNotPublicAndRecordsNothing() {
    Product product = draft();

    assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
    assertThat(product.getPublishedAt()).isNull();
    assertThat(product.getCreatedAt()).isEqualTo(NOW);
    assertThat(product.getUpdatedAt()).isEqualTo(NOW);
    assertThat(product.getVersion()).isNull();
    assertThat(product.pullEvents()).isEmpty();
  }

  @Test
  void draftNormalisesSkuSlugNameAndTags() {
    Product product = draft();

    assertThat(product.getSku().value()).isEqualTo("CAF-001");
    assertThat(product.getSlug().value()).isEqualTo("cafe-de-colombia-en-grano-caf-001");
    assertThat(product.getName()).isEqualTo("Café de Colombia en grano");
    assertThat(product.getSeller()).isEqualTo(seller);
    assertThat(product.getCategories()).containsExactly(coffee);
    assertThat(product.getTags())
        .containsExactlyInAnyOrder(new Tag("ecologico"), new Tag("comercio-justo"));
  }

  @Test
  void draftStripsNameAndDescription() {
    Product product = Product.draft(draftOf("  Café  ", "  d ", Set.of()), clock);

    assertThat(product.getName()).isEqualTo("Café");
    assertThat(product.getDescription()).isEqualTo("d");
  }

  @Test
  void draftWithoutDescriptionOrTagsOrCategoriesIsValid() {
    Product product =
        Product.draft(
            new ProductDraft(
                seller, new Sku("SKU-1"), "Café", null, Money.of("1", "EUR"), null, null),
            clock);

    assertThat(product.getDescription()).isEmpty();
    assertThat(product.getCategories()).isEmpty();
    assertThat(product.getTags()).isEmpty();
  }

  @Test
  void acceptsTheNameAndDescriptionLimits() {
    Product product = Product.draft(draftOf("a".repeat(160), "d".repeat(4000), Set.of()), clock);

    assertThat(product.getName()).hasSize(160);
    assertThat(product.getDescription()).hasSize(4000);
  }

  @Test
  void rejectsInvalidNames() {
    for (String name : new String[] {null, "  ", "a".repeat(161)}) {
      assertThatThrownBy(() -> Product.draft(draftOf(name, "", Set.of()), clock))
          .isInstanceOf(InvalidNameException.class)
          .hasFieldOrPropertyWithValue("code", "invalid-name");
    }
  }

  @Test
  void rejectsTooLongDescription() {
    assertThatThrownBy(() -> Product.draft(draftOf("Café", "d".repeat(4001), Set.of()), clock))
        .isInstanceOf(InvalidDescriptionException.class)
        .hasFieldOrPropertyWithValue("code", "invalid-description");
  }

  @Test
  void acceptsTenTagsAndRejectsMore() {
    assertThat(Product.draft(draftOf("Café", "", tags(10)), clock).getTags()).hasSize(10);
    assertThatThrownBy(() -> Product.draft(draftOf("Café", "", tags(11)), clock))
        .isInstanceOf(TooManyTagsException.class)
        .hasFieldOrPropertyWithValue("code", "too-many-tags");
  }

  @Test
  void publishMakesTheProductActiveAndRecordsProductPublished() {
    Product product = draft();

    product.publish(later);

    assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(product.getPublishedAt()).isEqualTo(LATER);
    assertThat(product.getUpdatedAt()).isEqualTo(LATER);
    assertThat(product.pullEvents())
        .containsExactly(
            new ProductPublished(
                product.getId(),
                new Sku("CAF-001"),
                "Café de Colombia en grano",
                Money.of("18.90", "EUR"),
                seller,
                LATER));
  }

  @Test
  void publishingTwiceIsRejected() {
    Product product = draft();
    product.publish(clock);

    assertThatThrownBy(() -> product.publish(clock))
        .isInstanceOf(ProductAlreadyPublishedException.class)
        .hasFieldOrPropertyWithValue("code", "product-already-published");
  }

  @Test
  void discontinuedProductCannotBePublished() {
    Product product = draft();
    product.discontinue("Sin proveedor", clock);

    assertThatThrownBy(() -> product.publish(clock))
        .isInstanceOf(ProductDiscontinuedException.class)
        .hasFieldOrPropertyWithValue("code", "product-discontinued");
  }

  @Test
  void priceChangeOfActiveProductRecordsOldAndNewPrice() {
    Product product = draft();
    product.publish(clock);
    product.pullEvents();

    product.changePrice(Money.of("16.50", "EUR"), later);

    assertThat(product.getPrice()).isEqualTo(Money.of("16.50", "EUR"));
    assertThat(product.getUpdatedAt()).isEqualTo(LATER);
    assertThat(product.pullEvents())
        .containsExactly(
            new ProductPriceChanged(
                product.getId(), Money.of("18.90", "EUR"), Money.of("16.50", "EUR"), LATER));
  }

  @Test
  void priceChangeOfDraftRecordsNothing() {
    Product product = draft();

    product.changePrice(Money.of("20.00", "EUR"), later);

    assertThat(product.getPrice()).isEqualTo(Money.of("20.00", "EUR"));
    assertThat(product.getUpdatedAt()).isEqualTo(LATER);
    assertThat(product.pullEvents()).isEmpty();
  }

  @Test
  void samePriceIsANoOp() {
    Product product = draft();
    product.publish(clock);
    product.pullEvents();

    product.changePrice(Money.of("18.9", "EUR"), later);

    assertThat(product.getUpdatedAt()).isEqualTo(NOW);
    assertThat(product.pullEvents()).isEmpty();
  }

  @Test
  void priceInAnotherCurrencyIsRejected() {
    Product product = draft();

    assertThatThrownBy(() -> product.changePrice(Money.of("20", "USD"), clock))
        .isInstanceOf(CurrencyMismatchException.class)
        .hasFieldOrPropertyWithValue("code", "currency-mismatch");
  }

  @Test
  void discontinuedProductPriceCannotChange() {
    Product product = draft();
    product.discontinue("Sin proveedor", clock);

    assertThatThrownBy(() -> product.changePrice(Money.of("20", "EUR"), clock))
        .isInstanceOf(ProductDiscontinuedException.class)
        .hasFieldOrPropertyWithValue("code", "product-discontinued");
  }

  @Test
  void discontinuingAPublishedProductRecordsProductDiscontinued() {
    Product product = draft();
    product.publish(clock);
    product.pullEvents();

    product.discontinue("Fin de temporada", later);

    assertThat(product.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
    assertThat(product.getUpdatedAt()).isEqualTo(LATER);
    assertThat(product.pullEvents())
        .containsExactly(new ProductDiscontinued(product.getId(), "Fin de temporada", LATER));
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

    product.discontinue("Otra vez", later);

    assertThat(product.getUpdatedAt()).isEqualTo(NOW);
    assertThat(product.pullEvents()).isEmpty();
  }

  @Test
  void pullEventsDrainsTheRecordedEvents() {
    Product product = draft();
    product.publish(clock);

    assertThat(product.pullEvents()).hasSize(1);
    assertThat(product.pullEvents()).isEmpty();
  }

  @Test
  void belongsToItsSellerOnly() {
    Product product = draft();

    assertThat(product.belongsTo(seller)).isTrue();
    assertThat(product.belongsTo(new SellerId("seller-bob"))).isFalse();
  }

  @Test
  void collectionsAreNotModifiableFromOutside() {
    Product product = draft();

    assertThatThrownBy(() -> product.getTags().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> product.getCategories().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void reconstituteRestoresTheState() {
    ProductState state =
        new ProductState(
            ProductId.newId(),
            new Sku("TEA-1"),
            new Slug("te-verde-tea-1"),
            "Té verde",
            "Hojas enteras",
            Money.of("4.50", "EUR"),
            ProductStatus.ACTIVE,
            seller,
            Set.of(coffee),
            Set.of(new Tag("te")),
            NOW,
            LATER,
            LATER,
            7L);

    Product product = Product.reconstitute(state);

    assertThat(product.state()).isEqualTo(state);
    assertThat(product.getId()).isEqualTo(state.id());
    assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(product.getVersion()).isEqualTo(7L);
    assertThat(product.getPublishedAt()).isEqualTo(LATER);
    assertThat(product.pullEvents()).isEmpty();
  }

  @Test
  void reconstitutedProductContinuesItsLifecycle() {
    Product product =
        Product.reconstitute(
            new ProductState(
                ProductId.newId(),
                new Sku("TEA-1"),
                new Slug("te-verde-tea-1"),
                "Té verde",
                "",
                Money.of("4.50", "EUR"),
                ProductStatus.ACTIVE,
                seller,
                Set.of(),
                Set.of(),
                NOW,
                NOW,
                NOW,
                1L));

    product.changePrice(Money.of("5", "EUR"), later);

    assertThat(product.pullEvents()).singleElement().isInstanceOf(ProductPriceChanged.class);
  }

  @Test
  void stateRoundTrips() {
    Product product = draft();
    product.publish(later);

    ProductState state = product.state();
    Product restored = Product.reconstitute(state);

    assertThat(restored.state()).isEqualTo(state);
    assertThat(state.status()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(state.sku()).isEqualTo(new Sku("CAF-001"));
    assertThat(state.publishedAt()).isEqualTo(LATER);
  }

  @Test
  void stateSetsAreUnmodifiableCopies() {
    Product product = draft();
    ProductState state = product.state();

    assertThatThrownBy(() -> state.tags().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> state.categories().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(product.getTags()).hasSize(2);
    assertThat(product.getCategories()).hasSize(1);
  }

  @Test
  void reconstituteCopiesTheSetsItIsGiven() {
    Set<Tag> tags = new LinkedHashSet<>(Set.of(new Tag("te")));
    Product product =
        Product.reconstitute(
            new ProductState(
                ProductId.newId(),
                new Sku("TEA-1"),
                new Slug("te-verde-tea-1"),
                "Té verde",
                "",
                Money.of("4.50", "EUR"),
                ProductStatus.DRAFT,
                seller,
                Set.of(),
                tags,
                NOW,
                null,
                NOW,
                null));

    tags.clear();

    assertThat(product.getTags()).containsExactly(new Tag("te"));
  }

  @Test
  void draftRequiresSellerSkuAndPrice() {
    Money price = Money.of("1", "EUR");
    Sku sku = new Sku("SKU-1");
    assertThatThrownBy(
            () -> Product.draft(new ProductDraft(null, sku, "Café", "", price, null, null), clock))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                Product.draft(new ProductDraft(seller, null, "Café", "", price, null, null), clock))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> Product.draft(new ProductDraft(seller, sku, "Café", "", null, null, null), clock))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void changePriceRequiresAPrice() {
    assertThatThrownBy(() -> draft().changePrice(null, clock))
        .isInstanceOf(NullPointerException.class);
  }
}
