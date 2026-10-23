package com.borjaglez.shop.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.catalog.application.exception.ProductNotFound;
import com.borjaglez.shop.catalog.application.fakes.InMemoryProductRepository;
import com.borjaglez.shop.catalog.application.port.in.PublishProductCommand;
import com.borjaglez.shop.catalog.domain.Money;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.Product.ProductState;
import com.borjaglez.shop.catalog.domain.ProductDraft;
import com.borjaglez.shop.catalog.domain.ProductStatus;
import com.borjaglez.shop.catalog.domain.SellerId;
import com.borjaglez.shop.catalog.domain.Sku;
import com.borjaglez.shop.catalog.domain.event.ProductPublished;
import com.borjaglez.shop.catalog.domain.exception.ProductAlreadyPublishedException;
import com.borjaglez.shop.kernel.error.NotFoundException;

class PublishProductHandlerTest {

  private static final Instant EARLIER = Instant.parse("2026-09-20T08:00:00Z");
  private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

  private final InMemoryProductRepository products = new InMemoryProductRepository();
  private final PublishProductHandler handler =
      new PublishProductHandler(products, Clock.fixed(NOW, ZoneOffset.UTC));

  private Product givenADraftOf(String seller) {
    Product product =
        Product.draft(
            new ProductDraft(
                new SellerId(seller),
                new Sku("CAF-900"),
                "Café de Honduras",
                "Tueste medio",
                Money.of("14.20", "EUR"),
                Set.of(),
                Set.of()),
            Clock.fixed(EARLIER, ZoneOffset.UTC));
    products.given(product);
    return product;
  }

  @Test
  void publishesTheSellersDraftAndAnnouncesIt() {
    Product draft = givenADraftOf("seller-ana");

    handler.handle(new PublishProductCommand(draft.getId().value(), "seller-ana"));

    ProductState stored = products.stored(draft.getId());
    assertThat(stored.status()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(stored.publishedAt()).isEqualTo(NOW);
    assertThat(stored.updatedAt()).isEqualTo(NOW);
    assertThat(stored.createdAt()).isEqualTo(EARLIER);
    assertThat(stored.version()).isEqualTo(1L);
    assertThat(products.events())
        .containsExactly(
            new ProductPublished(
                draft.getId(),
                new Sku("CAF-900"),
                "Café de Honduras",
                Money.of("14.20", "EUR"),
                new SellerId("seller-ana"),
                NOW));
  }

  @Test
  void aProductOfAnotherSellerIsNotFound() {
    Product draft = givenADraftOf("seller-ana");
    UUID id = draft.getId().value();

    assertThatThrownBy(() -> handler.handle(new PublishProductCommand(id, "seller-bruno")))
        .isInstanceOf(ProductNotFound.class)
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Unknown product " + id)
        .hasFieldOrPropertyWithValue("code", "product-not-found");
    assertThat(products.stored(draft.getId()).status()).isEqualTo(ProductStatus.DRAFT);
    assertThat(products.saves()).isZero();
    assertThat(products.events()).isEmpty();
  }

  @Test
  void anUnknownProductIsNotFound() {
    UUID id = UUID.randomUUID();

    assertThatThrownBy(() -> handler.handle(new PublishProductCommand(id, "seller-ana")))
        .isInstanceOf(ProductNotFound.class)
        .hasFieldOrPropertyWithValue("code", "product-not-found");
  }

  @Test
  void publishingTwiceIsRejected() {
    Product draft = givenADraftOf("seller-ana");
    UUID id = draft.getId().value();
    handler.handle(new PublishProductCommand(id, "seller-ana"));

    assertThatThrownBy(() -> handler.handle(new PublishProductCommand(id, "seller-ana")))
        .isInstanceOf(ProductAlreadyPublishedException.class)
        .hasFieldOrPropertyWithValue("code", "product-already-published");
    assertThat(products.saves()).isEqualTo(1);
    assertThat(products.events()).hasSize(1);
  }
}
