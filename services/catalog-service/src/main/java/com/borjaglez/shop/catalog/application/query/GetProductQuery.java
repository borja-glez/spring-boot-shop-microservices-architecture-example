package com.borjaglez.shop.catalog.application.query;

import java.util.Objects;

import com.borjaglez.cqrs.query.Query;

import lombok.Getter;

/** A published or discontinued product by its slug. Answered with a ProductDetail. */
@Getter
public class GetProductQuery extends Query {

  private final String slug;

  public GetProductQuery(String slug) {
    this.slug = Objects.requireNonNull(slug, "slug must not be null");
  }
}
