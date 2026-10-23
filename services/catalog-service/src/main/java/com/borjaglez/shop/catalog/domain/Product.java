package com.borjaglez.shop.catalog.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.borjaglez.shop.catalog.domain.event.ProductDiscontinued;
import com.borjaglez.shop.catalog.domain.event.ProductEvent;
import com.borjaglez.shop.catalog.domain.event.ProductPriceChanged;
import com.borjaglez.shop.catalog.domain.event.ProductPublished;
import com.borjaglez.shop.catalog.domain.exception.CurrencyMismatchException;
import com.borjaglez.shop.catalog.domain.exception.InvalidDescriptionException;
import com.borjaglez.shop.catalog.domain.exception.InvalidNameException;
import com.borjaglez.shop.catalog.domain.exception.ProductAlreadyPublishedException;
import com.borjaglez.shop.catalog.domain.exception.ProductDiscontinuedException;
import com.borjaglez.shop.catalog.domain.exception.TooManyTagsException;
import com.borjaglez.shop.kernel.domain.AggregateRoot;

/**
 * [DDD · Aggregate Root] A product offered by a seller.
 *
 * <p>State changes go through intention-revealing methods that enforce the lifecycle ({@code DRAFT
 * -> ACTIVE -> DISCONTINUED}) and record the {@link ProductEvent}s other contexts need. Events are
 * drained with {@link #pullEvents()} once the change is stored.
 */
public final class Product extends AggregateRoot<ProductEvent> {

  static final int MAX_TAGS = 10;
  private static final int MAX_NAME = 160;
  private static final int MAX_DESCRIPTION = 4000;

  private final ProductId id;
  private final Sku sku;
  private final Slug slug;
  private final String name;
  private final String description;
  private Money price;
  private ProductStatus status;
  private final SellerId seller;
  private final Set<CategoryId> categories;
  private final Set<Tag> tags;
  private final Instant createdAt;
  private Instant publishedAt;
  private Instant updatedAt;
  private final Long version;

  private Product(ProductState state) {
    this.id = state.id();
    this.sku = state.sku();
    this.slug = state.slug();
    this.name = state.name();
    this.description = state.description();
    this.price = state.price();
    this.status = state.status();
    this.seller = state.seller();
    this.categories = new LinkedHashSet<>(state.categories());
    this.tags = new LinkedHashSet<>(state.tags());
    this.createdAt = state.createdAt();
    this.publishedAt = state.publishedAt();
    this.updatedAt = state.updatedAt();
    this.version = state.version();
  }

  /** Creates an unpublished product. Checks the name, then the description, then the tags. */
  public static Product draft(ProductDraft draft, Clock clock) {
    Objects.requireNonNull(draft.seller(), "seller must not be null");
    Objects.requireNonNull(draft.sku(), "sku must not be null");
    Objects.requireNonNull(draft.price(), "price must not be null");
    String name = validName(draft.name());
    String description = validDescription(draft.description());
    Set<Tag> tags = validTags(orEmpty(draft.tags()));
    Instant now = clock.instant();
    return new Product(
        new ProductState(
            ProductId.newId(),
            draft.sku(),
            Slug.of(name, draft.sku()),
            name,
            description,
            draft.price(),
            ProductStatus.DRAFT,
            draft.seller(),
            orEmpty(draft.categories()),
            tags,
            now,
            null,
            now,
            null));
  }

  /** Rebuilds a stored product without validating or recording anything. */
  public static Product reconstitute(ProductState state) {
    return new Product(state);
  }

  /** Puts the product on sale. */
  public void publish(Clock clock) {
    switch (status) {
      case DISCONTINUED -> throw discontinued();
      case ACTIVE ->
          throw new ProductAlreadyPublishedException(
              "Product " + sku.value() + " is already published");
      case DRAFT -> {
        Instant now = clock.instant();
        status = ProductStatus.ACTIVE;
        publishedAt = now;
        updatedAt = now;
        record(new ProductPublished(id, sku, name, price, seller, now));
      }
    }
  }

  /** Changes the selling price. Only published products announce the change. */
  public void changePrice(Money newPrice, Clock clock) {
    Objects.requireNonNull(newPrice, "newPrice must not be null");
    if (status == ProductStatus.DISCONTINUED) {
      throw discontinued();
    }
    if (!newPrice.sameCurrencyAs(price)) {
      throw new CurrencyMismatchException(
          "Product " + sku.value() + " is priced in " + price.currency());
    }
    if (newPrice.equals(price)) {
      return;
    }
    Money oldPrice = price;
    Instant now = clock.instant();
    price = newPrice;
    updatedAt = now;
    if (status == ProductStatus.ACTIVE) {
      record(new ProductPriceChanged(id, oldPrice, newPrice, now));
    }
  }

  /** Withdraws the product for good. Discontinuing twice has no effect. */
  public void discontinue(String reason, Clock clock) {
    if (status == ProductStatus.DISCONTINUED) {
      return;
    }
    boolean wasPublic = status == ProductStatus.ACTIVE;
    Instant now = clock.instant();
    status = ProductStatus.DISCONTINUED;
    updatedAt = now;
    if (wasPublic) {
      record(new ProductDiscontinued(id, reason, now));
    }
  }

  public boolean belongsTo(SellerId candidate) {
    return seller.equals(candidate);
  }

  public ProductId getId() {
    return id;
  }

  public Sku getSku() {
    return sku;
  }

  public Slug getSlug() {
    return slug;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public Money getPrice() {
    return price;
  }

  public ProductStatus getStatus() {
    return status;
  }

  public SellerId getSeller() {
    return seller;
  }

  public Set<CategoryId> getCategories() {
    return Collections.unmodifiableSet(categories);
  }

  public Set<Tag> getTags() {
    return Collections.unmodifiableSet(tags);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  /** {@code null} until the product is published. */
  public Instant getPublishedAt() {
    return publishedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /** The version the product was read with; {@code null} for a product not stored yet. */
  public Long getVersion() {
    return version;
  }

  /** Snapshot of every field, for the persistence adapter. */
  public ProductState state() {
    return new ProductState(
        id,
        sku,
        slug,
        name,
        description,
        price,
        status,
        seller,
        categories,
        tags,
        createdAt,
        publishedAt,
        updatedAt,
        version);
  }

  private ProductDiscontinuedException discontinued() {
    return new ProductDiscontinuedException("Product " + sku.value() + " is discontinued");
  }

  private static String validName(String raw) {
    String name = raw == null ? "" : raw.strip();
    if (name.isEmpty() || name.length() > MAX_NAME) {
      throw new InvalidNameException("Name must have between 1 and " + MAX_NAME + " characters");
    }
    return name;
  }

  private static String validDescription(String raw) {
    String description = raw == null ? "" : raw.strip();
    if (description.length() > MAX_DESCRIPTION) {
      throw new InvalidDescriptionException(
          "Description must have at most " + MAX_DESCRIPTION + " characters");
    }
    return description;
  }

  /** Each tag is checked first; tags that read the same once slugified count once. */
  private static Set<Tag> validTags(Set<String> raw) {
    Set<Tag> tags = new LinkedHashSet<>();
    for (String candidate : raw) {
      tags.add(new Tag(candidate));
    }
    if (tags.size() > MAX_TAGS) {
      throw new TooManyTagsException("A product can have at most " + MAX_TAGS + " tags");
    }
    return tags;
  }

  private static <T> Set<T> orEmpty(Set<T> values) {
    return values == null ? Set.of() : values;
  }

  /** Every field of a {@link Product}, as the persistence adapter stores and restores it. */
  public record ProductState(
      ProductId id,
      Sku sku,
      Slug slug,
      String name,
      String description,
      Money price,
      ProductStatus status,
      SellerId seller,
      Set<CategoryId> categories,
      Set<Tag> tags,
      Instant createdAt,
      Instant publishedAt,
      Instant updatedAt,
      Long version) {

    public ProductState {
      categories = Collections.unmodifiableSet(new LinkedHashSet<>(categories));
      tags = Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    }
  }
}
