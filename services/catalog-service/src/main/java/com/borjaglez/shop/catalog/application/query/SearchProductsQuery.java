package com.borjaglez.shop.catalog.application.query;

import java.util.Objects;

import org.springframework.data.domain.Pageable;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.specrepository.core.QueryPlan;

import lombok.Getter;

/**
 * Public search over products on sale. Answered with a {@code Page<ProductCard>}.
 *
 * <p>Carries a {@link QueryPlan} built from the HTTP filters, so it is only dispatched on the local
 * query bus.
 */
@Getter
public class SearchProductsQuery extends Query {

  private final QueryPlan<Product> plan;
  private final Pageable pageable;

  public SearchProductsQuery(QueryPlan<Product> plan, Pageable pageable) {
    this.plan = Objects.requireNonNull(plan, "plan must not be null");
    this.pageable = Objects.requireNonNull(pageable, "pageable must not be null");
  }
}
