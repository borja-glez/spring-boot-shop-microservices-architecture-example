package com.borjaglez.shop.support.query;

import java.util.List;

import com.borjaglez.specrepository.core.ConditionGroupBuilder;
import com.borjaglez.specrepository.core.GroupCondition;
import com.borjaglez.specrepository.core.LogicalOperator;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryCondition;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.QueryPlanBuilder;
import com.borjaglez.specrepository.core.SpecificationQueryBuilder;
import com.borjaglez.specrepository.core.SubqueryCondition;

/**
 * Plan operations the fluent API of specification-repository does not offer.
 *
 * <p>Server conditions, default sorts, fetches, projections and grouping are added by deriving the
 * client plan ({@code repository.query(plan)} or {@code plan.toBuilder()}); this class only covers
 * what a derived builder cannot do, which is removing part of the client's own filters.
 *
 * <p>Since 1.0.0 a {@link QueryPlan} has no public constructor, so the plan is rebuilt through
 * {@link SpecificationQueryBuilder}. That only works for what a client can send: conditions and
 * groups of conditions, a sort and the whitelist, the shape of a plan resolved by
 * {@code @FilterableQuery}. Anything the server adds is added afterwards, on the plan this returns.
 */
public final class QueryPlans {

  private QueryPlans() {}

  /**
   * Returns a client plan without its top-level conditions on {@code field}. Used for disjunctive
   * faceting: the sellers facet ignores the seller filter so the other sellers stay selectable.
   * Alternative groups ({@code orFilter}) are kept whole, and so are the sort and the policy.
   *
   * @throws IllegalArgumentException if the plan carries more than a client sends over HTTP (server
   *     conditions, joins, fetches, selections, grouping, a lock, a subquery)
   */
  public static <T> QueryPlan<T> without(QueryPlan<T> plan, String field) {
    requireClientPlan(plan);
    QueryPlanBuilder<T> builder =
        SpecificationQueryBuilder.forEntity(plan.entityType())
            .allowedFields(plan.allowedFieldsPolicy())
            .sort(plan.sort());
    for (QueryCondition condition : plan.rootCondition().conditions()) {
      switch (condition) {
        case PredicateCondition p when p.field().equals(field) -> {}
        case PredicateCondition p ->
            builder.where(p.field(), p.operator(), p.value(), p.ignoreCase(), p.includeNulls());
        case GroupCondition g when g.logicalOperator() == LogicalOperator.OR ->
            builder.or(nested -> copy(g.conditions(), nested));
        case GroupCondition g -> builder.and(nested -> copy(g.conditions(), nested));
        case SubqueryCondition s -> throw unsupported(plan);
      }
    }
    return builder.build();
  }

  private static <T> void copy(List<QueryCondition> conditions, ConditionGroupBuilder<T> group) {
    for (QueryCondition condition : conditions) {
      switch (condition) {
        case PredicateCondition p ->
            group.where(p.field(), p.operator(), p.value(), p.ignoreCase(), p.includeNulls());
        case GroupCondition g when g.logicalOperator() == LogicalOperator.OR ->
            group.or(nested -> copy(g.conditions(), nested));
        case GroupCondition g -> group.and(nested -> copy(g.conditions(), nested));
        case SubqueryCondition s ->
            throw new IllegalArgumentException("A subquery cannot be copied: " + s);
      }
    }
  }

  private static void requireClientPlan(QueryPlan<?> plan) {
    boolean clientOnly =
        plan.rootCondition().logicalOperator() == LogicalOperator.AND
            && plan.serverCondition().conditions().isEmpty()
            && plan.joins().isEmpty()
            && plan.fetches().isEmpty()
            && plan.selections().isEmpty()
            && plan.projectionType() == null
            && plan.groupBy().isEmpty()
            && plan.having().isEmpty()
            && !plan.distinct()
            && !plan.lock().isLocked();
    if (!clientOnly) {
      throw unsupported(plan);
    }
  }

  private static IllegalArgumentException unsupported(QueryPlan<?> plan) {
    return new IllegalArgumentException(
        "Only a client plan (conditions, sort and whitelist) can drop conditions: " + plan);
  }
}
