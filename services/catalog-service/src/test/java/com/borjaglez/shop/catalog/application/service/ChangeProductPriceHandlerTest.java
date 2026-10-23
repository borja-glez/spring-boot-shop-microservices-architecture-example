package com.borjaglez.shop.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.catalog.application.exception.ProductNotFound;
import com.borjaglez.shop.catalog.application.fakes.InMemoryProductRepository;
import com.borjaglez.shop.catalog.application.port.in.ChangeProductPriceCommand;
import com.borjaglez.shop.catalog.domain.Money;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.Product.ProductState;
import com.borjaglez.shop.catalog.domain.ProductDraft;
import com.borjaglez.shop.catalog.domain.SellerId;
import com.borjaglez.shop.catalog.domain.Sku;
import com.borjaglez.shop.catalog.domain.event.ProductPriceChanged;
import com.borjaglez.shop.catalog.domain.exception.CurrencyMismatchException;
import com.borjaglez.shop.catalog.domain.exception.InvalidCurrencyException;
import com.borjaglez.shop.catalog.domain.exception.InvalidPriceException;
import com.borjaglez.shop.catalog.domain.exception.ProductDiscontinuedException;

class ChangeProductPriceHandlerTest {

  private static final Instant EARLIER = Instant.parse("2026-09-20T08:00:00Z");
  private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
  private static final Clock BEFORE = Clock.fixed(EARLIER, ZoneOffset.UTC);

  private final InMemoryProductRepository products = new InMemoryProductRepository();
  private final ChangeProductPriceHandler handler =
      new ChangeProductPriceHandler(products, Clock.fixed(NOW, ZoneOffset.UTC));

  private static Product draftOf(String seller) {
    return Product.draft(
        new ProductDraft(
            new SellerId(seller),
            new Sku("CAF-900"),
            "Café de Honduras",
            "",
            Money.of("14.20", "EUR"),
            Set.of(),
            Set.of()),
        BEFORE);
  }

  private Product given(Product product) {
    products.given(product);
    return product;
  }

  private Product givenAPublishedProductOf(String seller) {
    Product product = draftOf(seller);
    product.publish(BEFORE);
    return given(product);
  }

  private static ChangeProductPriceCommand change(
      Product product, String seller, String price, String currency) {
    return new ChangeProductPriceCommand(
        product.getId().value(), seller, new BigDecimal(price), currency);
  }

  @Test
  void changesThePriceOfAPublishedProductAndAnnouncesIt() {
    Product product = givenAPublishedProductOf("seller-ana");

    handler.handle(change(product, "seller-ana", "12.5", "eur"));

    ProductState stored = products.stored(product.getId());
    assertThat(stored.price()).isEqualTo(Money.of("12.50", "EUR"));
    assertThat(stored.updatedAt()).isEqualTo(NOW);
    assertThat(products.events())
        .containsExactly(
            new ProductPriceChanged(
                product.getId(), Money.of("14.20", "EUR"), Money.of("12.50", "EUR"), NOW));
  }

  @Test
  void theDraftPriceChangesWithoutAnnouncement() {
    Product product = given(draftOf("seller-ana"));

    handler.handle(change(product, "seller-ana", "15", "EUR"));

    assertThat(products.stored(product.getId()).price()).isEqualTo(Money.of("15", "EUR"));
    assertThat(products.saves()).isEqualTo(1);
    assertThat(products.events()).isEmpty();
  }

  @Test
  void anInvalidPriceIsRejected() {
    Product product = givenAPublishedProductOf("seller-ana");

    assertThatThrownBy(() -> handler.handle(change(product, "seller-ana", "0", "EUR")))
        .isInstanceOf(InvalidPriceException.class)
        .hasFieldOrPropertyWithValue("code", "invalid-price");
    assertThatThrownBy(() -> handler.handle(change(product, "seller-ana", "1", "EURO")))
        .isInstanceOf(InvalidCurrencyException.class)
        .hasFieldOrPropertyWithValue("code", "invalid-currency");
    assertThat(products.saves()).isZero();
  }

  @Test
  void aPriceInAnotherCurrencyIsRejected() {
    Product product = givenAPublishedProductOf("seller-ana");

    assertThatThrownBy(() -> handler.handle(change(product, "seller-ana", "15", "USD")))
        .isInstanceOf(CurrencyMismatchException.class)
        .hasFieldOrPropertyWithValue("code", "currency-mismatch");
  }

  @Test
  void aDiscontinuedProductKeepsItsPrice() {
    Product product = draftOf("seller-ana");
    product.discontinue("Sin proveedor", BEFORE);
    given(product);

    assertThatThrownBy(() -> handler.handle(change(product, "seller-ana", "15", "EUR")))
        .isInstanceOf(ProductDiscontinuedException.class)
        .hasFieldOrPropertyWithValue("code", "product-discontinued");
  }

  @Test
  void aProductOfAnotherSellerIsNotFoundWhateverThePrice() {
    Product product = givenAPublishedProductOf("seller-ana");

    assertThatThrownBy(() -> handler.handle(change(product, "seller-bruno", "0", "EURO")))
        .isInstanceOf(ProductNotFound.class)
        .hasFieldOrPropertyWithValue("code", "product-not-found");
    assertThat(products.stored(product.getId()).price()).isEqualTo(Money.of("14.20", "EUR"));
    assertThat(products.saves()).isZero();
  }

  @Test
  void anUnknownProductIsNotFound() {
    ChangeProductPriceCommand command =
        new ChangeProductPriceCommand(UUID.randomUUID(), "seller-ana", BigDecimal.TEN, "EUR");

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(ProductNotFound.class)
        .hasFieldOrPropertyWithValue("code", "product-not-found");
  }
}
