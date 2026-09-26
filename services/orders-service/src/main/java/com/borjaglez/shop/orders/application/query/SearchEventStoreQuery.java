package com.borjaglez.shop.orders.application.query;

import java.util.Objects;

import org.springframework.data.domain.Pageable;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.shop.eskit.StoredEvent;
import com.borjaglez.specrepository.core.QueryPlan;

import lombok.Getter;

/**
 * Rows of the event store matching the client plan. Answered with a {@code Page<StoredEventView>}.
 */
@Getter
public class SearchEventStoreQuery extends Query {

  private final QueryPlan<StoredEvent> plan;
  private final Pageable pageable;

  public SearchEventStoreQuery(QueryPlan<StoredEvent> plan, Pageable pageable) {
    this.plan = Objects.requireNonNull(plan, "plan must not be null");
    this.pageable = Objects.requireNonNull(pageable, "pageable must not be null");
  }
}
