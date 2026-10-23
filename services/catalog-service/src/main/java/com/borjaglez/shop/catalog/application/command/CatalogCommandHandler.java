package com.borjaglez.shop.catalog.application.command;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.shop.catalog.domain.CategoryEntity;
import com.borjaglez.shop.catalog.domain.CategoryJpaRepository;
import com.borjaglez.shop.catalog.domain.MoneyEmbeddable;
import com.borjaglez.shop.catalog.domain.ProductEntity;
import com.borjaglez.shop.catalog.domain.ProductEntityDraft;
import com.borjaglez.shop.catalog.domain.ProductJpaRepository;
import com.borjaglez.shop.catalog.domain.SellerEntity;
import com.borjaglez.shop.catalog.domain.SellerJpaRepository;
import com.borjaglez.shop.eskit.EventStore;
import com.borjaglez.shop.kernel.error.BusinessRuleViolationException;
import com.borjaglez.shop.kernel.error.ConflictException;
import com.borjaglez.shop.kernel.error.NotFoundException;
import com.borjaglez.specrepository.core.Operators;

/**
 * Write side of the catalog.
 *
 * <p>Each command runs in its own transaction. Events recorded by the aggregate are written to the
 * es-kit outbox in that same transaction and reach other services through Kafka.
 */
@CommandHandler
public class CatalogCommandHandler {

  private final ProductJpaRepository products;
  private final SellerJpaRepository sellers;
  private final CategoryJpaRepository categories;
  private final EventStore eventStore;
  private final Clock clock;

  public CatalogCommandHandler(
      ProductJpaRepository products,
      SellerJpaRepository sellers,
      CategoryJpaRepository categories,
      EventStore eventStore,
      Clock clock) {
    this.products = products;
    this.sellers = sellers;
    this.categories = categories;
    this.eventStore = eventStore;
    this.clock = clock;
  }

  @HandleCommand
  @Transactional
  public UUID create(CreateProductCommand command) {
    SellerEntity seller = seller(command.getSellerId());
    MoneyEmbeddable price = new MoneyEmbeddable(command.getPrice(), command.getCurrency());
    ProductEntity product =
        ProductEntity.draft(
            new ProductEntityDraft(
                seller,
                command.getSku(),
                command.getName(),
                command.getDescription(),
                price,
                categories(command.getCategorySlugs()),
                command.getTags()),
            clock);
    if (products.query().where("sku", Operators.EQUALS, product.getSku()).count() > 0) {
      throw new ConflictException(
          "duplicate-sku", "Another product already uses SKU " + product.getSku());
    }
    products.save(product);
    publish(product);
    return product.getId();
  }

  @HandleCommand
  @Transactional
  public void publish(PublishProductCommand command) {
    ProductEntity product = ownedProduct(command.getProductId(), command.getSellerId());
    product.publish(clock);
    publish(product);
  }

  @HandleCommand
  @Transactional
  public void changePrice(ChangeProductPriceCommand command) {
    ProductEntity product = ownedProduct(command.getProductId(), command.getSellerId());
    product.changePrice(new MoneyEmbeddable(command.getPrice(), command.getCurrency()), clock);
    publish(product);
  }

  @HandleCommand
  @Transactional
  public void discontinue(DiscontinueProductCommand command) {
    ProductEntity product = ownedProduct(command.getProductId(), command.getSellerId());
    product.discontinue(command.getReason(), clock);
    publish(product);
  }

  /**
   * Records the product's events in the outbox, in the same transaction as the product. The relay
   * publishes them to Kafka after the commit, so a rolled-back change never announces anything.
   */
  private void publish(ProductEntity product) {
    var events = product.pullEvents();
    if (!events.isEmpty()) {
      eventStore.record("product", product.getId().toString(), events);
    }
  }

  private SellerEntity seller(String sellerId) {
    return sellers
        .query()
        .where("id", Operators.EQUALS, sellerId)
        .findOne()
        .orElseThrow(() -> new NotFoundException("seller-not-found", "Unknown seller " + sellerId));
  }

  /** Products of other sellers are reported as missing so their existence is not revealed. */
  private ProductEntity ownedProduct(UUID productId, String sellerId) {
    return products
        .query()
        .where("id", Operators.EQUALS, productId)
        .where("seller.id", Operators.EQUALS, sellerId)
        .findOne()
        .orElseThrow(
            () -> new NotFoundException("product-not-found", "Unknown product " + productId));
  }

  private Set<CategoryEntity> categories(Set<String> slugs) {
    if (slugs.isEmpty()) {
      return Set.of();
    }
    List<CategoryEntity> found =
        categories.query().where("slug", Operators.IN, List.copyOf(slugs)).findAll();
    if (found.size() != slugs.size()) {
      Set<String> missing = new HashSet<>(slugs);
      missing.removeAll(found.stream().map(CategoryEntity::getSlug).collect(Collectors.toSet()));
      throw new BusinessRuleViolationException(
          "unknown-category", "Unknown categories: " + String.join(", ", missing));
    }
    return Set.copyOf(found);
  }
}
