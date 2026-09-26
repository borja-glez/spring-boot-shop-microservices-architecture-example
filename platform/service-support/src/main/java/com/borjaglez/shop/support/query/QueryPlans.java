package com.borjaglez.shop.support.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.FetchInstruction;
import com.borjaglez.specrepository.core.FieldSelection;
import com.borjaglez.specrepository.core.FilterOperator;
import com.borjaglez.specrepository.core.GroupCondition;
import com.borjaglez.specrepository.core.HavingCondition;
import com.borjaglez.specrepository.core.JoinMode;
import com.borjaglez.specrepository.core.LogicalOperator;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryCondition;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.Selection;

/**
 * Derives new plans from a plan received over HTTP.
 *
 * <p>A service often needs to compose server-side conditions (such as the owner of the data) onto a
 * {@link QueryPlan} parsed from the client's request. This helper rebuilds the plan record with the
 * extra conditions while keeping the client's conditions and joins untouched; the whitelist is
 * checked on the client part first, so server conditions may use fields the client cannot.
 */
public final class QueryPlans {

  private static final Set<FilterOperator> TEXT_SEARCH =
      Set.of(
          Operators.CONTAINS, Operators.NOT_CONTAINS, Operators.STARTS_WITH, Operators.ENDS_WITH);

  private QueryPlans() {}

  /**
   * Returns a plan that also requires every given server condition (logical AND).
   *
   * <p>The whitelist of a plan applies to all its conditions and is only checked when the query
   * runs, so a server condition on a field the client may not filter, such as the owner of the
   * data, would be rejected. This method therefore checks the client plan against its own policy
   * first, failing with {@link DisallowedFieldException} as the repository would, and returns a
   * plan that allows every field.
   */
  public static <T> QueryPlan<T> requiring(QueryPlan<T> plan, QueryCondition... conditions) {
    checkClientFields(plan);
    List<QueryCondition> all = new ArrayList<>();
    all.add(plan.rootCondition());
    all.addAll(Arrays.asList(conditions));
    return copy(
        plan,
        new GroupCondition(LogicalOperator.AND, all),
        plan.fetches(),
        plan.sort(),
        AllowedFieldsPolicy.allowAll());
  }

  /**
   * Returns a plan whose text searches ({@code contains}, {@code notcontains}, {@code startswith},
   * {@code endswith}) on the given fields ignore case and accents.
   *
   * <p>The HTTP filter syntax cannot ask for case-insensitive matching, but shoppers type "cafe"
   * and expect "Café", so the service decides it for the fields it knows are text.
   */
  public static <T> QueryPlan<T> ignoringCase(QueryPlan<T> plan, Set<String> textFields) {
    GroupCondition root = ignoringCase(plan.rootCondition(), textFields);
    return copy(plan, root, plan.fetches(), plan.sort());
  }

  private static GroupCondition ignoringCase(GroupCondition group, Set<String> textFields) {
    List<QueryCondition> conditions = new ArrayList<>();
    for (QueryCondition condition : group.conditions()) {
      conditions.add(
          switch (condition) {
            case GroupCondition nested -> ignoringCase(nested, textFields);
            case PredicateCondition predicate
                when textFields.contains(predicate.field())
                    && TEXT_SEARCH.contains(predicate.operator()) ->
                new PredicateCondition(
                    predicate.field(),
                    predicate.operator(),
                    predicate.value(),
                    true,
                    predicate.includeNulls());
            default -> condition;
          });
    }
    return new GroupCondition(group.logicalOperator(), conditions);
  }

  /**
   * Returns a plan without the top-level conditions on {@code field}. Used for disjunctive
   * faceting: the sellers facet ignores the seller filter so the other sellers stay selectable.
   * Alternative groups ({@code orFilter}) are kept whole.
   */
  public static <T> QueryPlan<T> without(QueryPlan<T> plan, String field) {
    List<QueryCondition> kept =
        plan.rootCondition().conditions().stream()
            .filter(c -> !(c instanceof PredicateCondition p && p.field().equals(field)))
            .toList();
    return copy(
        plan,
        new GroupCondition(plan.rootCondition().logicalOperator(), kept),
        plan.fetches(),
        plan.sort());
  }

  /** Returns a plan that also fetches the given associations with a left join. */
  public static <T> QueryPlan<T> fetching(QueryPlan<T> plan, String... paths) {
    List<FetchInstruction> fetches = new ArrayList<>(plan.fetches());
    for (String path : paths) {
      fetches.add(new FetchInstruction(path, JoinMode.LEFT));
    }
    return copy(plan, plan.rootCondition(), fetches, plan.sort());
  }

  /** Returns the plan with {@code sort} when the client did not ask for any order. */
  public static <T> QueryPlan<T> sortedByDefault(QueryPlan<T> plan, Sort sort) {
    return plan.sort().isSorted() ? plan : copy(plan, plan.rootCondition(), plan.fetches(), sort);
  }

  /**
   * Returns the plan read straight into {@code type} ({@code selectInto}): the server picks the
   * columns, in the order of the constructor of {@code type}, and the rows are never managed
   * entities. Conditions, joins, sort and the client's field policy are kept; fetches are dropped
   * (they make no sense without entities).
   */
  public static <T> QueryPlan<T> projecting(QueryPlan<T> plan, Class<?> type, String... fields) {
    if (fields.length == 0) {
      throw new IllegalArgumentException("A projection needs at least one field");
    }
    List<String> projected = List.of(fields);
    return new QueryPlan<>(
        plan.entityType(),
        plan.rootCondition(),
        plan.joins(),
        List.of(),
        projected,
        projected.stream().<Selection>map(FieldSelection::new).toList(),
        type,
        List.of(),
        List.of(),
        plan.sort(),
        plan.distinct(),
        plan.allowedFieldsPolicy());
  }

  /** Returns a grouping plan over the same rows: same conditions and joins, no fetches, no sort. */
  public static <T> QueryPlan<T> grouping(
      QueryPlan<T> plan, List<String> groupBy, List<Selection> selections) {
    return grouping(plan, groupBy, selections, List.of());
  }

  /**
   * Like {@link #grouping(QueryPlan, List, List)}, keeping only the groups that match {@code
   * having}.
   */
  public static <T> QueryPlan<T> grouping(
      QueryPlan<T> plan,
      List<String> groupBy,
      List<Selection> selections,
      List<HavingCondition> having) {
    return new QueryPlan<>(
        plan.entityType(),
        plan.rootCondition(),
        plan.joins(),
        List.of(),
        List.of(),
        List.copyOf(selections),
        null,
        List.copyOf(groupBy),
        List.copyOf(having),
        Sort.unsorted(),
        false,
        plan.allowedFieldsPolicy());
  }

  private static <T> QueryPlan<T> copy(
      QueryPlan<T> plan, GroupCondition root, List<FetchInstruction> fetches, Sort sort) {
    return copy(plan, root, fetches, sort, plan.allowedFieldsPolicy());
  }

  private static <T> QueryPlan<T> copy(
      QueryPlan<T> plan,
      GroupCondition root,
      List<FetchInstruction> fetches,
      Sort sort,
      AllowedFieldsPolicy policy) {
    return new QueryPlan<>(
        plan.entityType(),
        root,
        plan.joins(),
        List.copyOf(fetches),
        plan.projections(),
        plan.selections(),
        plan.projectionType(),
        plan.groupBy(),
        plan.having(),
        sort,
        plan.distinct(),
        policy);
  }

  /** The checks the repository makes on filters, HAVING and sort, done before adding ours. */
  private static void checkClientFields(QueryPlan<?> plan) {
    AllowedFieldsPolicy policy = plan.allowedFieldsPolicy();
    if (policy.isAllowAll()) {
      return;
    }
    checkFilters(policy, plan.rootCondition());
    plan.having().forEach(having -> policy.validateFilter(having.field()));
    plan.sort().forEach(order -> policy.validateSort(order.getProperty()));
  }

  private static void checkFilters(AllowedFieldsPolicy policy, GroupCondition group) {
    for (QueryCondition condition : group.conditions()) {
      switch (condition) {
        case PredicateCondition predicate -> policy.validateFilter(predicate.field());
        case GroupCondition nested -> checkFilters(policy, nested);
        default ->
            // The HTTP syntax cannot express subqueries; a plan with one was built in code.
            throw new IllegalArgumentException(
                "Unexpected condition in a client plan: " + condition.getClass().getSimpleName());
      }
    }
  }
}
