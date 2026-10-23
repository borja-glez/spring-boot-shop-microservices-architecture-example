package com.borjaglez.shop.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.borjaglez.shop.catalog.application.exception.DuplicateSku;
import com.borjaglez.shop.catalog.application.exception.SellerNotFound;
import com.borjaglez.shop.catalog.application.exception.UnknownCategory;
import com.borjaglez.shop.catalog.application.fakes.InMemoryCategories;
import com.borjaglez.shop.catalog.application.fakes.InMemoryProductRepository;
import com.borjaglez.shop.catalog.application.fakes.InMemorySellers;
import com.borjaglez.shop.catalog.application.port.in.CreateProductCommand;
import com.borjaglez.shop.catalog.domain.CategoryId;
import com.borjaglez.shop.catalog.domain.Money;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.Product.ProductState;
import com.borjaglez.shop.catalog.domain.ProductDraft;
import com.borjaglez.shop.catalog.domain.ProductId;
import com.borjaglez.shop.catalog.domain.ProductStatus;
import com.borjaglez.shop.catalog.domain.SellerId;
import com.borjaglez.shop.catalog.domain.Sku;
import com.borjaglez.shop.catalog.domain.Slug;
import com.borjaglez.shop.catalog.domain.Tag;
import com.borjaglez.shop.kernel.error.BusinessRuleViolationException;
import com.borjaglez.shop.kernel.error.ConflictException;
import com.borjaglez.shop.kernel.error.DomainException;
import com.borjaglez.shop.kernel.error.NotFoundException;

class CreateProductHandlerTest {

  private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

  private final InMemoryProductRepository products = new InMemoryProductRepository();
  private final InMemorySellers sellers = new InMemorySellers();
  private final InMemoryCategories categories = new InMemoryCategories();
  private final CreateProductHandler handler =
      new CreateProductHandler(products, sellers, categories, Clock.fixed(NOW, ZoneOffset.UTC));

  private final SellerId ana = sellers.add("seller-ana", "Tostadores Ana", "Madrid");
  private final CategoryId coffee = categories.add("cafe-e-infusiones");

  /** What a seller sends, valid unless a test changes it. */
  private record Request(
      String seller,
      String sku,
      String name,
      String description,
      String price,
      String currency,
      Set<String> categories,
      Set<String> tags) {

    static Request valid() {
      return new Request(
          "seller-ana",
          "caf-900",
          "Café de Honduras",
          "Tueste medio",
          "14.20",
          "EUR",
          Set.of("cafe-e-infusiones"),
          Set.of(" Ecológico "));
    }

    Request seller(String value) {
      return new Request(value, sku, name, description, price, currency, categories, tags);
    }

    Request sku(String value) {
      return new Request(seller, value, name, description, price, currency, categories, tags);
    }

    Request name(String value) {
      return new Request(seller, sku, value, description, price, currency, categories, tags);
    }

    Request description(String value) {
      return new Request(seller, sku, name, value, price, currency, categories, tags);
    }

    Request price(String value) {
      return new Request(seller, sku, name, description, value, currency, categories, tags);
    }

    Request currency(String value) {
      return new Request(seller, sku, name, description, price, value, categories, tags);
    }

    Request categories(Set<String> value) {
      return new Request(seller, sku, name, description, price, currency, value, tags);
    }

    Request tags(Set<String> value) {
      return new Request(seller, sku, name, description, price, currency, categories, value);
    }

    CreateProductCommand command() {
      return new CreateProductCommand(
          seller, sku, name, description, new BigDecimal(price), currency, categories, tags);
    }
  }

  private static Set<String> tags(int count) {
    Set<String> tags = new LinkedHashSet<>();
    IntStream.rangeClosed(1, count).forEach(i -> tags.add("tag" + i));
    return tags;
  }

  private void givenAProductWithSku(String sku) {
    products.given(
        Product.draft(
            new ProductDraft(
                ana, new Sku(sku), "Otro café", "", Money.of("9.99", "EUR"), Set.of(), Set.of()),
            Clock.fixed(NOW, ZoneOffset.UTC)));
  }

  private String firstError(Request request) {
    return catchThrowableOfType(DomainException.class, () -> handler.handle(request.command()))
        .code();
  }

  @Test
  void createsADraftOfTheSeller() {
    UUID id = handler.handle(Request.valid().command());

    ProductState stored = products.stored(new ProductId(id));
    assertThat(stored.status()).isEqualTo(ProductStatus.DRAFT);
    assertThat(stored.seller()).isEqualTo(ana);
    assertThat(stored.sku()).isEqualTo(new Sku("CAF-900"));
    assertThat(stored.slug()).isEqualTo(new Slug("cafe-de-honduras-caf-900"));
    assertThat(stored.name()).isEqualTo("Café de Honduras");
    assertThat(stored.description()).isEqualTo("Tueste medio");
    assertThat(stored.price()).isEqualTo(Money.of("14.20", "EUR"));
    assertThat(stored.categories()).containsExactly(coffee);
    assertThat(stored.tags()).containsExactly(new Tag("ecologico"));
    assertThat(stored.createdAt()).isEqualTo(NOW);
    assertThat(stored.updatedAt()).isEqualTo(NOW);
    assertThat(stored.publishedAt()).isNull();
  }

  @Test
  void aDraftAnnouncesNothing() {
    handler.handle(Request.valid().command());

    assertThat(products.saves()).isEqualTo(1);
    assertThat(products.events()).isEmpty();
  }

  @Test
  void aProductNeedsNoCategoriesNorTags() {
    UUID id = handler.handle(Request.valid().categories(Set.of()).tags(Set.of()).command());

    ProductState stored = products.stored(new ProductId(id));
    assertThat(stored.categories()).isEmpty();
    assertThat(stored.tags()).isEmpty();
  }

  @Test
  void anUnknownSellerIsNotFound() {
    assertThatThrownBy(() -> handler.handle(Request.valid().seller("seller-nobody").command()))
        .isInstanceOf(SellerNotFound.class)
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Unknown seller seller-nobody")
        .hasFieldOrPropertyWithValue("code", "seller-not-found");
    assertThat(products.saves()).isZero();
  }

  @Test
  void unknownCategoriesAreRejected() {
    Request request = Request.valid().categories(Set.of("cafe-e-infusiones", "tes", "no-existe"));

    assertThatThrownBy(() -> handler.handle(request.command()))
        .isInstanceOf(UnknownCategory.class)
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasMessage("Unknown categories: no-existe, tes")
        .hasFieldOrPropertyWithValue("code", "unknown-category");
    assertThat(products.saves()).isZero();
  }

  static Stream<Arguments> invalidRequests() {
    Request valid = Request.valid();
    return Stream.of(
        Arguments.of(valid.price("0"), "invalid-price"),
        Arguments.of(valid.price("1.999"), "invalid-price"),
        Arguments.of(valid.currency("EURO"), "invalid-currency"),
        Arguments.of(valid.sku("a b"), "invalid-sku"),
        Arguments.of(valid.name(" "), "invalid-name"),
        Arguments.of(valid.description("d".repeat(4001)), "invalid-description"),
        Arguments.of(valid.tags(Set.of("!!!")), "invalid-tag"),
        Arguments.of(valid.tags(tags(11)), "too-many-tags"));
  }

  @ParameterizedTest
  @MethodSource("invalidRequests")
  void eachBrokenRuleHasItsCode(Request request, String code) {
    assertThat(firstError(request)).isEqualTo(code);
    assertThat(products.saves()).isZero();
  }

  @Test
  void aSkuInUseIsAConflict() {
    givenAProductWithSku("CAF-900");

    assertThatThrownBy(() -> handler.handle(Request.valid().command()))
        .isInstanceOf(DuplicateSku.class)
        .isInstanceOf(ConflictException.class)
        .hasMessage("Another product already uses SKU CAF-900")
        .hasFieldOrPropertyWithValue("code", "duplicate-sku");
    assertThat(products.saves()).isZero();
    assertThat(products.all()).hasSize(1);
  }

  @Test
  void reportsTheBrokenRulesInAFixedOrder() {
    givenAProductWithSku("CAF-001");
    Request request =
        new Request(
            "seller-nobody",
            "a b",
            " ",
            "d".repeat(4001),
            "-1",
            "EURO",
            Set.of("no-existe"),
            Set.of("!!!", "tag1", "tag2", "tag3", "tag4", "tag5", "tag6", "tag7", "tag8", "tag9"));

    assertThat(firstError(request)).isEqualTo("seller-not-found");
    request = request.seller("seller-ana");
    assertThat(firstError(request)).isEqualTo("invalid-price");
    request = request.price("4.10");
    assertThat(firstError(request)).isEqualTo("invalid-currency");
    request = request.currency("EUR");
    assertThat(firstError(request)).isEqualTo("unknown-category");
    request = request.categories(Set.of("cafe-e-infusiones"));
    assertThat(firstError(request)).isEqualTo("invalid-sku");
    request = request.sku("CAF-001");
    assertThat(firstError(request)).isEqualTo("invalid-name");
    request = request.name("Café de Kenia");
    assertThat(firstError(request)).isEqualTo("invalid-description");
    request = request.description("");
    assertThat(firstError(request)).isEqualTo("invalid-tag");
    request = request.tags(tags(11));
    assertThat(firstError(request)).isEqualTo("too-many-tags");
    request = request.tags(tags(10));
    assertThat(firstError(request)).isEqualTo("duplicate-sku");
    request = request.sku("CAF-002");
    assertThat(handler.handle(request.command())).isNotNull();
  }
}
