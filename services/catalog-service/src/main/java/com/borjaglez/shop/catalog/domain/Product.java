package com.borjaglez.shop.catalog.domain;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.shop.contracts.catalog.ProductDiscontinued;
import com.borjaglez.shop.contracts.catalog.ProductPriceChanged;
import com.borjaglez.shop.contracts.catalog.ProductPublished;
import com.borjaglez.shop.support.error.BusinessRuleViolationException;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A product offered by a seller. Aggregate root of the catalog.
 *
 * <p>State changes go through intention-revealing methods that enforce the lifecycle ({@code DRAFT
 * → ACTIVE → DISCONTINUED}) and record the integration events other services need. Events are
 * drained with {@link #pullEvents()} once the change is persisted.
 */
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

  static final int MAX_TAGS = 10;
  private static final Pattern SKU = Pattern.compile("[A-Z0-9-]{3,40}");
  private static final int MAX_NAME = 160;
  private static final int MAX_DESCRIPTION = 4000;
  private static final int MAX_TAG = 30;

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 40)
  private String sku;

  @Column(nullable = false, unique = true, length = 220)
  private String slug;

  @Column(nullable = false, length = MAX_NAME)
  private String name;

  @Column(nullable = false, length = MAX_DESCRIPTION)
  private String description;

  @Embedded private Money price;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProductStatus status;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "seller_id", nullable = false)
  private Seller seller;

  @ManyToMany
  @JoinTable(
      name = "product_category",
      joinColumns = @JoinColumn(name = "product_id"),
      inverseJoinColumns = @JoinColumn(name = "category_id"))
  private Set<Category> categories = new LinkedHashSet<>();

  @ElementCollection
  @CollectionTable(name = "product_tag", joinColumns = @JoinColumn(name = "product_id"))
  @Column(name = "tag", nullable = false, length = MAX_TAG)
  private Set<String> tags = new LinkedHashSet<>();

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Version private Long version;

  @Transient private final List<Event> events = new ArrayList<>();

  /** Creates an unpublished product. */
  public static Product draft(ProductDraft draft, Clock clock) {
    Objects.requireNonNull(draft.seller(), "seller must not be null");
    Objects.requireNonNull(draft.price(), "price must not be null");
    Product product = new Product();
    product.id = UUID.randomUUID();
    product.sku = validSku(draft.sku());
    product.name = validName(draft.name());
    product.slug = Slugs.slugify(product.name) + "-" + product.sku.toLowerCase(Locale.ROOT);
    product.description = validDescription(draft.description());
    product.price = draft.price();
    product.status = ProductStatus.DRAFT;
    product.seller = draft.seller();
    product.categories = new LinkedHashSet<>(orEmpty(draft.categories()));
    product.tags = validTags(orEmpty(draft.tags()));
    product.createdAt = OffsetDateTime.now(clock);
    product.updatedAt = product.createdAt;
    return product;
  }

  /** Puts the product on sale. */
  public void publish(Clock clock) {
    switch (status) {
      case DISCONTINUED -> throw discontinued();
      case ACTIVE ->
          throw new BusinessRuleViolationException(
              "product-already-published", "Product " + sku + " is already published");
      case DRAFT -> {
        status = ProductStatus.ACTIVE;
        publishedAt = OffsetDateTime.now(clock);
        touch(clock);
        events.add(
            new ProductPublished(id, sku, name, price.amount(), price.currency(), seller.getId()));
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
      throw new BusinessRuleViolationException(
          "currency-mismatch", "Product " + sku + " is priced in " + price.currency());
    }
    if (newPrice.equals(price)) {
      return;
    }
    Money oldPrice = price;
    price = newPrice;
    touch(clock);
    if (status == ProductStatus.ACTIVE) {
      events.add(
          new ProductPriceChanged(id, oldPrice.amount(), newPrice.amount(), price.currency()));
    }
  }

  /** Withdraws the product for good. Discontinuing twice has no effect. */
  public void discontinue(String reason, Clock clock) {
    if (status == ProductStatus.DISCONTINUED) {
      return;
    }
    boolean wasPublic = status == ProductStatus.ACTIVE;
    status = ProductStatus.DISCONTINUED;
    touch(clock);
    if (wasPublic) {
      events.add(new ProductDiscontinued(id, reason));
    }
  }

  /** Returns the events recorded since the last call and forgets them. */
  public List<Event> pullEvents() {
    List<Event> drained = List.copyOf(events);
    events.clear();
    return drained;
  }

  public Set<Category> getCategories() {
    return Collections.unmodifiableSet(categories);
  }

  public Set<String> getTags() {
    return Collections.unmodifiableSet(tags);
  }

  private void touch(Clock clock) {
    updatedAt = OffsetDateTime.now(clock);
  }

  private BusinessRuleViolationException discontinued() {
    return new BusinessRuleViolationException(
        "product-discontinued", "Product " + sku + " is discontinued");
  }

  private static String validSku(String raw) {
    String sku = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    if (!SKU.matcher(sku).matches()) {
      throw new BusinessRuleViolationException(
          "invalid-sku", "SKU must be 3-40 characters from A-Z, 0-9 and '-'");
    }
    return sku;
  }

  private static String validName(String raw) {
    String name = raw == null ? "" : raw.strip();
    if (name.isEmpty() || name.length() > MAX_NAME) {
      throw new BusinessRuleViolationException(
          "invalid-name", "Name must have between 1 and " + MAX_NAME + " characters");
    }
    return name;
  }

  private static String validDescription(String raw) {
    String description = raw == null ? "" : raw.strip();
    if (description.length() > MAX_DESCRIPTION) {
      throw new BusinessRuleViolationException(
          "invalid-description",
          "Description must have at most " + MAX_DESCRIPTION + " characters");
    }
    return description;
  }

  private static Set<String> validTags(Set<String> raw) {
    Set<String> tags = new LinkedHashSet<>();
    for (String candidate : raw) {
      String tag = candidate == null ? "" : Slugs.slugify(candidate);
      if (tag.isEmpty() || tag.length() > MAX_TAG) {
        throw new BusinessRuleViolationException(
            "invalid-tag", "Tags must have between 1 and " + MAX_TAG + " characters");
      }
      tags.add(tag);
    }
    if (tags.size() > MAX_TAGS) {
      throw new BusinessRuleViolationException(
          "too-many-tags", "A product can have at most " + MAX_TAGS + " tags");
    }
    return tags;
  }

  private static <T> Set<T> orEmpty(Set<T> values) {
    return values == null ? Set.of() : values;
  }
}
