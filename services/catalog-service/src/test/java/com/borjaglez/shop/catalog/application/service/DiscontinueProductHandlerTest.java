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
import com.borjaglez.shop.catalog.application.port.in.DiscontinueProductCommand;
import com.borjaglez.shop.catalog.domain.Money;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.Product.ProductState;
import com.borjaglez.shop.catalog.domain.ProductDraft;
import com.borjaglez.shop.catalog.domain.ProductStatus;
import com.borjaglez.shop.catalog.domain.SellerId;
import com.borjaglez.shop.catalog.domain.Sku;
import com.borjaglez.shop.catalog.domain.event.ProductDiscontinued;

class DiscontinueProductHandlerTest {

  private static final Instant EARLIER = Instant.parse("2026-09-20T08:00:00Z");
  private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
  private static final Clock BEFORE = Clock.fixed(EARLIER, ZoneOffset.UTC);

  private final InMemoryProductRepository products = new InMemoryProductRepository();
  private final DiscontinueProductHandler handler =
      new DiscontinueProductHandler(products, Clock.fixed(NOW, ZoneOffset.UTC));

  private Product givenADraftOf(String seller, boolean published) {
    Product product =
        Product.draft(
            new ProductDraft(
                new SellerId(seller),
                new Sku("CAF-900"),
                "Café de Honduras",
                "",
                Money.of("14.20", "EUR"),
                Set.of(),
                Set.of()),
            BEFORE);
    if (published) {
      product.publish(BEFORE);
    }
    products.given(product);
    return product;
  }

  private static DiscontinueProductCommand discontinue(Product product, String seller) {
    return new DiscontinueProductCommand(product.getId().value(), seller, "Fin de cosecha");
  }

  @Test
  void withdrawsAPublishedProductAndAnnouncesIt() {
    Product product = givenADraftOf("seller-ana", true);

    handler.handle(discontinue(product, "seller-ana"));

    ProductState stored = products.stored(product.getId());
    assertThat(stored.status()).isEqualTo(ProductStatus.DISCONTINUED);
    assertThat(stored.updatedAt()).isEqualTo(NOW);
    assertThat(products.events())
        .containsExactly(new ProductDiscontinued(product.getId(), "Fin de cosecha", NOW));
  }

  @Test
  void withdrawingADraftAnnouncesNothing() {
    Product product = givenADraftOf("seller-ana", false);

    handler.handle(discontinue(product, "seller-ana"));

    assertThat(products.stored(product.getId()).status()).isEqualTo(ProductStatus.DISCONTINUED);
    assertThat(products.saves()).isEqualTo(1);
    assertThat(products.events()).isEmpty();
  }

  @Test
  void aProductOfAnotherSellerIsNotFound() {
    Product product = givenADraftOf("seller-ana", true);

    assertThatThrownBy(() -> handler.handle(discontinue(product, "seller-bruno")))
        .isInstanceOf(ProductNotFound.class)
        .hasFieldOrPropertyWithValue("code", "product-not-found");
    assertThat(products.stored(product.getId()).status()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(products.saves()).isZero();
  }

  @Test
  void anUnknownProductIsNotFound() {
    DiscontinueProductCommand command =
        new DiscontinueProductCommand(UUID.randomUUID(), "seller-ana", "Fin de cosecha");

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(ProductNotFound.class)
        .hasFieldOrPropertyWithValue("code", "product-not-found");
  }
}
