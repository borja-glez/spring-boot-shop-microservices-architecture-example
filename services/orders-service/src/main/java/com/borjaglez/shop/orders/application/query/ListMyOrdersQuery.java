package com.borjaglez.shop.orders.application.query;

import java.util.Objects;

import org.springframework.data.domain.Pageable;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.shop.orders.domain.OrderView;
import com.borjaglez.specrepository.core.QueryPlan;

import lombok.Getter;

/**
 * The customer's orders, filtered by the client plan. Answered with a {@code Page<OrderSummary>}.
 * The handler adds the customer condition, so the plan cannot reach other customers' orders.
 */
@Getter
public class ListMyOrdersQuery extends Query {

  private final String customerId;
  private final QueryPlan<OrderView> plan;
  private final Pageable pageable;

  public ListMyOrdersQuery(String customerId, QueryPlan<OrderView> plan, Pageable pageable) {
    this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
    this.plan = Objects.requireNonNull(plan, "plan must not be null");
    this.pageable = Objects.requireNonNull(pageable, "pageable must not be null");
  }
}
