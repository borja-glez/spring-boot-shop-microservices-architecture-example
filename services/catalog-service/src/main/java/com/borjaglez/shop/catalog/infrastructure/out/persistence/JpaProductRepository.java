package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.Product.ProductState;
import com.borjaglez.shop.catalog.domain.ProductId;
import com.borjaglez.shop.catalog.domain.ProductRepository;
import com.borjaglez.shop.catalog.domain.Sku;
import com.borjaglez.shop.catalog.domain.event.ProductEvent;
import com.borjaglez.shop.eskit.DomainEventStore;
import com.borjaglez.specrepository.core.Operators;

/**
 * [Hexagonal · adaptador driven] Stores {@link Product}s in PostgreSQL through JPA, and the events
 * each change collected in the event store, which hands their integration events to the outbox of
 * spring-boot-cqrs. Everything joins the caller's transaction: the product, its events and their
 * announcements commit together or not at all.
 *
 * <p>A product keeps the version it was read with. Saving it over a newer version, written by
 * another transaction in the meantime, fails with an {@link
 * org.springframework.dao.OptimisticLockingFailureException} instead of overwriting that change.
 */
public class JpaProductRepository implements ProductRepository {

  /** The stream type of the products' events in the event store. */
  static final String STREAM_TYPE = "product";

  private final ProductJpaRepository products;
  private final SellerJpaRepository sellers;
  private final CategoryJpaRepository categories;
  private final DomainEventStore events;

  public JpaProductRepository(
      ProductJpaRepository products,
      SellerJpaRepository sellers,
      CategoryJpaRepository categories,
      DomainEventStore events) {
    this.products = products;
    this.sellers = sellers;
    this.categories = categories;
    this.events = events;
  }

  @Override
  public Optional<Product> findById(ProductId id) {
    return products
        .query()
        .where("id", Operators.EQUALS, id.value())
        .findOne()
        .map(ProductMapper::toState)
        .map(Product::reconstitute);
  }

  @Override
  public boolean existsBySku(Sku sku) {
    return products.query().where("sku", Operators.EQUALS, sku.value()).count() > 0;
  }

  /**
   * Writes the product at once, so a conflict with another transaction fails here, and then records
   * its events. A product without a version is inserted; any other updates the stored one, which
   * must still have the version the product was read with, and only if something changed.
   */
  @Override
  public void save(Product product) {
    ProductState state = product.state();
    ProductEntity changed = toEntity(state);
    if (state.version() == null) {
      products.saveAndFlush(changed);
    } else {
      stored(state).update(changed);
      products.flush();
    }
    List<ProductEvent> recorded = product.pullEvents();
    if (!recorded.isEmpty()) {
      events.record(STREAM_TYPE, state.id().value().toString(), recorded);
    }
  }

  /** The seller and the categories are references: only their ids are written. */
  private ProductEntity toEntity(ProductState state) {
    Set<CategoryEntity> productCategories =
        state.categories().stream()
            .map(category -> categories.getReferenceById(category.value()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    return ProductMapper.toEntity(
        state, sellers.getReferenceById(state.seller().value()), productCategories);
  }

  /**
   * The stored product, if it still has the version the product was read with.
   *
   * @throws ObjectOptimisticLockingFailureException if another transaction changed it since
   */
  private ProductEntity stored(ProductState state) {
    UUID id = state.id().value();
    return products
        .query()
        .where("id", Operators.EQUALS, id)
        .findOne()
        .filter(entity -> entity.getVersion().equals(state.version()))
        .orElseThrow(() -> new ObjectOptimisticLockingFailureException(ProductEntity.class, id));
  }
}
