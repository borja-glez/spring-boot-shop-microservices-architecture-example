package com.borjaglez.shop.catalog.application.port.in;

import java.util.Objects;

import com.borjaglez.cqrs.query.Query;

/**
 * [Hexagonal · puerto de entrada] The page of a published or discontinued product, by its slug.
 * Answered with a {@link ProductDetail}.
 */
public final class GetProductQuery extends Query {

  private final String slug;

  public GetProductQuery(String slug) {
    this.slug = Objects.requireNonNull(slug, "slug must not be null");
  }

  public String getSlug() {
    return slug;
  }
}
