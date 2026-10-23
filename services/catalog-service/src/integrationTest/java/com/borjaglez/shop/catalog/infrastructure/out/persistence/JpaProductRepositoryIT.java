package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.shop.catalog.domain.CategoryRepository;
import com.borjaglez.shop.catalog.domain.Money;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.Product.ProductState;
import com.borjaglez.shop.catalog.domain.ProductDraft;
import com.borjaglez.shop.catalog.domain.ProductId;
import com.borjaglez.shop.catalog.domain.ProductRepository;
import com.borjaglez.shop.catalog.domain.ProductStatus;
import com.borjaglez.shop.catalog.domain.SellerId;
import com.borjaglez.shop.catalog.domain.Sku;
import com.borjaglez.shop.catalog.domain.Tag;
import com.borjaglez.shop.contracts.catalog.ProductPublished;

/**
 * Products stored as JPA entities, with their events in the event store and their integration
 * events in the outbox of spring-boot-cqrs. Products are created for seller-fermin in the
 * "despensa" category.
 */
@CatalogPersistenceTest
class JpaProductRepositoryIT {

  private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final Clock LATER = Clock.fixed(NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC);

  @Autowired ProductRepository products;
  @Autowired CategoryRepository categories;
  @Autowired TestEntityManager entityManager;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactionManager;

  private static Sku uniqueSku() {
    return new Sku("JPA-" + ThreadLocalRandom.current().nextInt(100_000, 999_999));
  }

  private Product draft(Sku sku) {
    return Product.draft(
        new ProductDraft(
            new SellerId("seller-fermin"),
            sku,
            "Garbanzos de Fuentesaúco",
            "Legumbre con Indicación Geográfica",
            Money.of("4.10", "EUR"),
            Set.copyOf(categories.findIdsBySlugs(Set.of("despensa")).values()),
            Set.of("Legumbres", "IGP")),
        CLOCK);
  }

  /** Reads the product back from the database, not from the persistence context. */
  private Product reload(ProductId id) {
    entityManager.clear();
    return products.findById(id).orElseThrow();
  }

  private <T> T fetch(Supplier<T> work) {
    return new TransactionTemplate(transactionManager).execute(status -> work.get());
  }

  private void inTransaction(Runnable work) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
  }

  private static ProductState withVersion(ProductState state, long version) {
    return new ProductState(
        state.id(),
        state.sku(),
        state.slug(),
        state.name(),
        state.description(),
        state.price(),
        state.status(),
        state.seller(),
        state.categories(),
        state.tags(),
        state.createdAt(),
        state.publishedAt(),
        state.updatedAt(),
        version);
  }

  @Test
  void savesAndReloadsAProduct() {
    Product product = draft(uniqueSku());

    products.save(product);

    Product reloaded = reload(product.getId());
    assertThat(reloaded.state()).isEqualTo(withVersion(product.state(), 0));
    assertThat(reloaded.getTags()).containsExactlyInAnyOrder(new Tag("legumbres"), new Tag("igp"));
    assertThat(reloaded.getCategories()).hasSize(1);
  }

  @Test
  void savesTheChangesOfAStoredProduct() {
    Product product = draft(uniqueSku());
    products.save(product);
    Product stored = reload(product.getId());

    stored.publish(LATER);
    products.save(stored);

    Product reloaded = reload(product.getId());
    assertThat(reloaded.state()).isEqualTo(withVersion(stored.state(), 1));
    assertThat(reloaded.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(reloaded.getPublishedAt()).isEqualTo(LATER.instant());
  }

  @Test
  void anUnknownProductIsNotFound() {
    assertThat(products.findById(ProductId.newId())).isEmpty();
  }

  @Test
  void existsBySku() {
    Sku sku = uniqueSku();
    products.save(draft(sku));

    assertThat(products.existsBySku(sku)).isTrue();
    assertThat(products.existsBySku(new Sku("CAF-001"))).isTrue();
    assertThat(products.existsBySku(uniqueSku())).isFalse();
  }

  @Test
  void aDraftRecordsNoEvents() {
    Product product = draft(uniqueSku());

    products.save(product);

    assertThat(storedEventTypes(product.getId())).isEmpty();
    assertThat(outboxRows(product.getId())).isEmpty();
  }

  @Test
  void savingRecordsTheDomainEventsAndTheirTranslations() {
    Product product = draft(uniqueSku());
    products.save(product);
    Product stored = reload(product.getId());
    stored.publish(CLOCK);
    stored.changePrice(Money.of("3.95", "EUR"), CLOCK);
    stored.discontinue("Fin de cosecha", LATER);

    products.save(stored);

    assertThat(storedEventTypes(product.getId()))
        .containsExactly(
            "product-published.v1", "product-price-changed.v1", "product-discontinued.v1");
    assertThat(jdbc.queryForList(eventStoreRowsSql(), product.getId().value().toString()))
        .allSatisfy(
            row -> {
              assertThat(row.get("stream_type")).isEqualTo("product");
              assertThat(row.get("version")).isNull();
              assertThat(row.get("published_at")).isNotNull();
            });
    assertThat(outboxRows(product.getId()))
        .extracting(row -> row.get("event_name"), row -> row.get("event_class"))
        .containsExactly(
            tuple(
                "shop.catalog.1.event.product.product-published", ProductPublished.class.getName()),
            tuple(
                "shop.catalog.1.event.product.product-price-changed",
                "com.borjaglez.shop.contracts.catalog.ProductPriceChanged"),
            tuple(
                "shop.catalog.1.event.product.product-discontinued",
                "com.borjaglez.shop.contracts.catalog.ProductDiscontinued"));
    assertThat(outboxRows(product.getId()).getFirst())
        .containsEntry("sku", stored.getSku().value())
        .containsEntry("seller_id", "seller-fermin")
        .containsEntry("published_at", null);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentUpdatesConflict() throws Exception {
    Product product = draft(uniqueSku());
    inTransaction(() -> products.save(product));
    CountDownLatch bothRead = new CountDownLatch(2);
    CountDownLatch firstCommitted = new CountDownLatch(1);
    ExecutorService sellers = Executors.newFixedThreadPool(2);
    try {
      Future<?> first =
          sellers.submit(
              () -> {
                inTransaction(
                    () -> {
                      Product read = products.findById(product.getId()).orElseThrow();
                      read.changePrice(Money.of("3.95", "EUR"), LATER);
                      arriveAndWait(bothRead);
                      products.save(read);
                    });
                firstCommitted.countDown();
              });
      Future<?> second =
          sellers.submit(
              () ->
                  inTransaction(
                      () -> {
                        Product read = products.findById(product.getId()).orElseThrow();
                        read.changePrice(Money.of("4.50", "EUR"), LATER);
                        arriveAndWait(bothRead);
                        waitFor(firstCommitted);
                        products.save(read);
                      }));

      first.get(30, TimeUnit.SECONDS);
      assertThatThrownBy(() -> second.get(30, TimeUnit.SECONDS))
          .hasCauseInstanceOf(OptimisticLockingFailureException.class);
    } finally {
      sellers.shutdownNow();
    }

    ProductState stored = fetch(() -> products.findById(product.getId()).orElseThrow()).state();
    assertThat(stored.price()).isEqualTo(Money.of("3.95", "EUR"));
    assertThat(stored.version()).isEqualTo(1L);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void aStaleCopyCannotOverwriteANewerVersion() {
    Product product = draft(uniqueSku());
    inTransaction(() -> products.save(product));
    Product first = fetch(() -> products.findById(product.getId()).orElseThrow());
    Product second = fetch(() -> products.findById(product.getId()).orElseThrow());
    first.changePrice(Money.of("3.95", "EUR"), LATER);
    inTransaction(() -> products.save(first));

    second.changePrice(Money.of("4.50", "EUR"), LATER);

    assertThatThrownBy(() -> inTransaction(() -> products.save(second)))
        .isInstanceOf(OptimisticLockingFailureException.class);
    assertThat(fetch(() -> products.findById(product.getId()).orElseThrow()).getPrice())
        .isEqualTo(Money.of("3.95", "EUR"));
  }

  private static void arriveAndWait(CountDownLatch latch) {
    latch.countDown();
    waitFor(latch);
  }

  private static void waitFor(CountDownLatch latch) {
    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static String eventStoreRowsSql() {
    return "select stream_type, version, event_type, published_at from event_store"
        + " where stream_id = ? order by global_position";
  }

  private List<String> storedEventTypes(ProductId id) {
    return jdbc.queryForList(eventStoreRowsSql(), id.value().toString()).stream()
        .map(row -> (String) row.get("event_type"))
        .toList();
  }

  /** The outbox rows of the integration events of a product, in the order they were written. */
  private List<Map<String, Object>> outboxRows(ProductId id) {
    return jdbc.queryForList(
        "select event_name, event_class, published_at,"
            + " convert_from(payload, 'UTF8')::jsonb ->> 'sku' as sku,"
            + " convert_from(payload, 'UTF8')::jsonb ->> 'sellerId' as seller_id"
            + " from cqrs_outbox"
            + " where convert_from(payload, 'UTF8')::jsonb ->> 'productId' = ? order by id",
        id.value().toString());
  }
}
