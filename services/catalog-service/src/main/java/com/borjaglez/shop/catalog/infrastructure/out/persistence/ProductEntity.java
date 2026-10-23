package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

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
import jakarta.persistence.Version;

import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.ProductStatus;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The stored form of a {@link Product}: a row of {@code product} with its categories and tags. It
 * holds state only; the rules live in {@link Product}. Its {@code version} is the optimistic lock:
 * a change written over a newer version fails instead of overwriting it.
 */
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 40)
  private String sku;

  @Column(nullable = false, unique = true, length = 220)
  private String slug;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false, length = 4000)
  private String description;

  @Embedded private MoneyEmbeddable price;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProductStatus status;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "seller_id", nullable = false)
  private SellerEntity seller;

  @ManyToMany
  @JoinTable(
      name = "product_category",
      joinColumns = @JoinColumn(name = "product_id"),
      inverseJoinColumns = @JoinColumn(name = "category_id"))
  private Set<CategoryEntity> categories = new LinkedHashSet<>();

  @ElementCollection
  @CollectionTable(name = "product_tag", joinColumns = @JoinColumn(name = "product_id"))
  @Column(name = "tag", nullable = false, length = 30)
  private Set<String> tags = new LinkedHashSet<>();

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Version private Long version;

  /**
   * A product with every column. A {@code null} version is a product not stored yet; any other is
   * the version the product was read with.
   */
  public ProductEntity(
      UUID id,
      String sku,
      String slug,
      String name,
      String description,
      MoneyEmbeddable price,
      ProductStatus status,
      SellerEntity seller,
      Set<CategoryEntity> categories,
      Set<String> tags,
      OffsetDateTime createdAt,
      OffsetDateTime publishedAt,
      OffsetDateTime updatedAt,
      Long version) {
    this.id = id;
    this.sku = sku;
    this.slug = slug;
    this.name = name;
    this.description = description;
    this.price = price;
    this.status = status;
    this.seller = seller;
    this.categories = new LinkedHashSet<>(categories);
    this.tags = new LinkedHashSet<>(tags);
    this.createdAt = createdAt;
    this.publishedAt = publishedAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  /**
   * Takes the values of {@code changed}, a newer form of this same product. Values that did not
   * change are left as they are, so a product saved without changes is not written again.
   */
  public void update(ProductEntity changed) {
    sku = changed.sku;
    slug = changed.slug;
    name = changed.name;
    description = changed.description;
    price = changed.price;
    status = changed.status;
    seller = changed.seller;
    createdAt = changed.createdAt;
    publishedAt = changed.publishedAt;
    updatedAt = changed.updatedAt;
    categories.retainAll(changed.categories);
    categories.addAll(changed.categories);
    tags.retainAll(changed.tags);
    tags.addAll(changed.tags);
  }

  public Set<CategoryEntity> getCategories() {
    return Collections.unmodifiableSet(categories);
  }

  public Set<String> getTags() {
    return Collections.unmodifiableSet(tags);
  }
}
