package com.borjaglez.shop.catalog.application.query;

import java.util.Objects;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.shop.catalog.domain.ProductEntity;
import com.borjaglez.specrepository.core.QueryPlan;

import lombok.Getter;

/** Refinement counts for the products a public search matches. Answered with CatalogFacets. */
@Getter
public class GetCatalogFacetsQuery extends Query {

  private final QueryPlan<ProductEntity> plan;

  public GetCatalogFacetsQuery(QueryPlan<ProductEntity> plan) {
    this.plan = Objects.requireNonNull(plan, "plan must not be null");
  }
}
