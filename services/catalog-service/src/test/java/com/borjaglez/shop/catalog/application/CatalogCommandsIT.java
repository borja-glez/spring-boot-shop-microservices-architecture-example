package com.borjaglez.shop.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.validation.ConstraintViolationException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.shop.catalog.application.command.ChangeProductPriceCommand;
import com.borjaglez.shop.catalog.application.command.CreateProductCommand;
import com.borjaglez.shop.catalog.application.command.DiscontinueProductCommand;
import com.borjaglez.shop.catalog.application.command.PublishProductCommand;
import com.borjaglez.shop.catalog.domain.ProductRepository;
import com.borjaglez.shop.catalog.domain.ProductStatus;
import com.borjaglez.shop.eskit.StoredEvent;
import com.borjaglez.shop.eskit.StoredEventRepository;
import com.borjaglez.shop.support.error.BusinessRuleViolationException;
import com.borjaglez.shop.support.error.ConflictException;
import com.borjaglez.shop.support.error.NotFoundException;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.specrepository.core.Operators;

/**
 * Drives the write side through the real command bus and database. Products are created for
 * seller-fermin in the "despensa" category, which the query tests leave alone.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({PostgresTestConfiguration.class, KafkaTestConfiguration.class})
class CatalogCommandsIT {

  private static final String SELLER = "seller-fermin";

  @Autowired CommandBus commands;
  @Autowired ProductRepository products;
  @Autowired StoredEventRepository outbox;
  @Autowired TransactionTemplate transactions;

  private static final String PUBLISHED = "shop.catalog.1.event.product.product-published";
  private static final String PRICE_CHANGED = "shop.catalog.1.event.product.product-price-changed";
  private static final String DISCONTINUED = "shop.catalog.1.event.product.product-discontinued";

  /** Events the product recorded in the outbox, in the order they were written. */
  private List<String> outboxOf(UUID productId) {
    return outbox
        .query()
        .where("streamType", Operators.EQUALS, "product")
        .where("streamId", Operators.EQUALS, productId.toString())
        .sort(Sort.by("globalPosition"))
        .findAll()
        .stream()
        .map(StoredEvent::getEventType)
        .toList();
  }

  private static String uniqueSku() {
    return "TST-" + ThreadLocalRandom.current().nextInt(100_000, 999_999);
  }

  private UUID create(String sku) {
    return commands.dispatchAndReceive(
        new CreateProductCommand(
            SELLER,
            sku,
            "Garbanzos de Fuentesaúco",
            "Legumbre con Indicación Geográfica",
            new BigDecimal("4.10"),
            "EUR",
            Set.of("despensa"),
            Set.of("Legumbres")));
  }

  @Test
  void createStoresADraftAndAnnouncesNothing() {
    UUID id = create(uniqueSku());

    transactions.executeWithoutResult(
        tx -> {
          var stored = products.findById(id).orElseThrow();
          assertThat(stored.getStatus()).isEqualTo(ProductStatus.DRAFT);
          assertThat(stored.getTags()).containsExactly("legumbres");
        });
    assertThat(outboxOf(id)).isEmpty();
  }

  @Test
  void publishAnnouncesTheProduct() {
    String sku = uniqueSku();
    UUID id = create(sku);

    commands.dispatchAndWait(new PublishProductCommand(id, SELLER));

    assertThat(products.findById(id).orElseThrow().getStatus()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(outboxOf(id)).containsExactly(PUBLISHED);
  }

  @Test
  void priceChangeOfAPublishedProductIsAnnounced() {
    UUID id = create(uniqueSku());
    commands.dispatchAndWait(new PublishProductCommand(id, SELLER));

    commands.dispatchAndWait(
        new ChangeProductPriceCommand(id, SELLER, new BigDecimal("3.95"), "EUR"));

    assertThat(outboxOf(id)).containsExactly(PUBLISHED, PRICE_CHANGED);
  }

  @Test
  void discontinuingAPublishedProductIsAnnounced() {
    UUID id = create(uniqueSku());
    commands.dispatchAndWait(new PublishProductCommand(id, SELLER));

    commands.dispatchAndWait(new DiscontinueProductCommand(id, SELLER, "Fin de cosecha"));

    assertThat(products.findById(id).orElseThrow().getStatus())
        .isEqualTo(ProductStatus.DISCONTINUED);
    assertThat(outboxOf(id)).containsExactly(PUBLISHED, DISCONTINUED);
  }

  @Test
  void duplicatedSkuIsAConflict() {
    assertThatThrownBy(() -> create("CAF-001"))
        .isInstanceOf(ConflictException.class)
        .hasFieldOrPropertyWithValue("code", "duplicate-sku");
  }

  @Test
  void unknownSellerIsNotFound() {
    assertThatThrownBy(
            () ->
                commands.dispatchAndReceive(
                    new CreateProductCommand(
                        "seller-nobody",
                        uniqueSku(),
                        "Algo",
                        "",
                        BigDecimal.ONE,
                        "EUR",
                        Set.of(),
                        Set.of())))
        .isInstanceOf(NotFoundException.class)
        .hasFieldOrPropertyWithValue("code", "seller-not-found");
  }

  @Test
  void unknownCategoryIsRejected() {
    assertThatThrownBy(
            () ->
                commands.dispatchAndReceive(
                    new CreateProductCommand(
                        SELLER,
                        uniqueSku(),
                        "Algo",
                        "",
                        BigDecimal.ONE,
                        "EUR",
                        Set.of("no-existe"),
                        Set.of())))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "unknown-category");
  }

  @Test
  void sellersCannotTouchProductsOfOtherSellers() {
    UUID id = create(uniqueSku());

    assertThatThrownBy(() -> commands.dispatchAndWait(new PublishProductCommand(id, "seller-ana")))
        .isInstanceOf(NotFoundException.class)
        .hasFieldOrPropertyWithValue("code", "product-not-found");
  }

  @Test
  void invalidCommandsAreRejectedBeforeReachingTheHandler() {
    assertThatThrownBy(
            () ->
                commands.dispatchAndReceive(
                    new CreateProductCommand(
                        SELLER, "", " ", "", new BigDecimal("-1"), "EUR", Set.of(), Set.of())))
        .isInstanceOf(ConstraintViolationException.class);
  }
}
