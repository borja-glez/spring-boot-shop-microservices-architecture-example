package com.borjaglez.shop.support.query;

import java.util.List;

import com.borjaglez.specrepository.core.GroupCondition;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryCondition;
import com.borjaglez.specrepository.core.QueryPlan;

/**
 * Plan operations the fluent API of specification-repository does not offer.
 *
 * <p>Server conditions, default sorts, fetches, projections and grouping are added by deriving the
 * client plan ({@code repository.query(plan)} or {@code plan.toBuilder()}); this class only covers
 * what a derived builder cannot do, which is removing part of the client's own filters.
 */
public final class QueryPlans {

  private QueryPlans() {}

  /**
   * Returns a plan without the top-level client conditions on {@code field}. Used for disjunctive
   * faceting: the sellers facet ignores the seller filter so the other sellers stay selectable.
   * Alternative groups ({@code orFilter}) are kept whole, and so are the server conditions, the
   * policy and every other part of the plan.
   */
  public static <T> QueryPlan<T> without(QueryPlan<T> plan, String field) {
    GroupCondition client = plan.rootCondition();
    List<QueryCondition> kept =
        client.conditions().stream()
            .filter(c -> !(c instanceof PredicateCondition p && p.field().equals(field)))
            .toList();
    return new QueryPlan<>(
        plan.entityType(),
        new GroupCondition(client.logicalOperator(), kept),
        plan.serverCondition(),
        plan.joins(),
        plan.fetches(),
        plan.projections(),
        plan.selections(),
        plan.projectionType(),
        plan.groupBy(),
        plan.having(),
        plan.sort(),
        plan.distinct(),
        plan.allowedFieldsPolicy(),
        plan.lock());
  }
}
