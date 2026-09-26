package com.borjaglez.shop.support.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.borjaglez.specrepository.core.AggregateFunction;
import com.borjaglez.specrepository.core.AggregateSelection;
import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.FetchInstruction;
import com.borjaglez.specrepository.core.FieldSelection;
import com.borjaglez.specrepository.core.GroupCondition;
import com.borjaglez.specrepository.core.HavingCondition;
import com.borjaglez.specrepository.core.JoinMode;
import com.borjaglez.specrepository.core.LogicalOperator;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.SpecificationQueryBuilder;

class QueryPlansTest {

  /** Plans only need a type; nothing is queried. */
  static class Item {}

  private static final AllowedFieldsPolicy POLICY =
      AllowedFieldsPolicy.of(Set.of("name", "status", "description", "sku"), Set.of("name"));

  private static QueryPlan<Item> clientPlan() {
    return SpecificationQueryBuilder.forEntity(Item.class)
        .where("name", Operators.CONTAINS, "café")
        .or(
            g ->
                g.where("description", Operators.STARTS_WITH, "Tueste")
                    .where("sku", Operators.EQUALS, "X"))
        .sort(Sort.by("name"))
        .allowedFields(POLICY)
        .build();
  }

  @Test
  void requiringAndsTheServerConditionWithTheClientOnes() {
    PredicateCondition onSale =
        new PredicateCondition("status", Operators.EQUALS, "ACTIVE", false, false);

    QueryPlan<Item> plan = QueryPlans.requiring(clientPlan(), onSale);

    assertThat(plan.rootCondition().logicalOperator()).isEqualTo(LogicalOperator.AND);
    assertThat(plan.rootCondition().conditions())
        .containsExactly(clientPlan().rootCondition(), onSale);
    assertThat(plan.sort()).isEqualTo(Sort.by("name"));
    assertThat(plan.allowedFieldsPolicy().isAllowAll()).isTrue();
  }

  @Test
  void serverConditionsMayUseFieldsTheClientCannotFilter() {
    PredicateCondition owner =
        new PredicateCondition("owner", Operators.EQUALS, "lucia", false, false);

    QueryPlan<Item> plan = QueryPlans.requiring(clientPlan(), owner);

    assertThat(plan.rootCondition().conditions()).contains(owner);
  }

  @Test
  void theClientPlanIsCheckedAgainstItsOwnPolicyFirst() {
    QueryPlan<Item> filtersOwner =
        SpecificationQueryBuilder.forEntity(Item.class)
            .and(g -> g.where("owner", Operators.EQUALS, "mateo"))
            .allowedFields(POLICY)
            .build();
    QueryPlan<Item> sortsByStatus =
        SpecificationQueryBuilder.forEntity(Item.class)
            .sort(Sort.by("status"))
            .allowedFields(POLICY)
            .build();
    QueryPlan<Item> havingOnOwner =
        SpecificationQueryBuilder.forEntity(Item.class)
            .groupBy("name")
            .having(AggregateFunction.COUNT, "owner", Operators.GREATER_THAN, 1)
            .allowedFields(POLICY)
            .build();

    assertThatThrownBy(() -> QueryPlans.requiring(filtersOwner))
        .isInstanceOf(DisallowedFieldException.class);
    assertThatThrownBy(() -> QueryPlans.requiring(sortsByStatus))
        .isInstanceOf(DisallowedFieldException.class);
    assertThatThrownBy(() -> QueryPlans.requiring(havingOnOwner))
        .isInstanceOf(DisallowedFieldException.class);
  }

  @Test
  void subqueriesAreNotExpectedInClientPlans() {
    QueryPlan<Item> withSubquery =
        SpecificationQueryBuilder.forEntity(Item.class)
            .exists("parts", sub -> sub.where("name", Operators.EQUALS, "x"))
            .allowedFields(POLICY)
            .build();

    assertThatThrownBy(() -> QueryPlans.requiring(withSubquery))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void plansBuiltInCodeAreNotChecked() {
    QueryPlan<Item> serverPlan =
        SpecificationQueryBuilder.forEntity(Item.class)
            .exists("parts", sub -> sub.where("name", Operators.EQUALS, "x"))
            .build();

    assertThat(QueryPlans.requiring(serverPlan).rootCondition().conditions()).hasSize(1);
  }

  @Test
  void fetchingAddsLeftFetches() {
    QueryPlan<Item> plan = QueryPlans.fetching(clientPlan(), "seller");

    assertThat(plan.fetches()).containsExactly(new FetchInstruction("seller", JoinMode.LEFT));
  }

  @Test
  void textSearchesBecomeCaseInsensitiveOnTheGivenFieldsOnly() {
    QueryPlan<Item> plan = QueryPlans.ignoringCase(clientPlan(), Set.of("name", "description"));

    PredicateCondition name = (PredicateCondition) plan.rootCondition().conditions().get(0);
    GroupCondition or = (GroupCondition) plan.rootCondition().conditions().get(1);
    PredicateCondition description = (PredicateCondition) or.conditions().get(0);
    PredicateCondition sku = (PredicateCondition) or.conditions().get(1);

    assertThat(name.ignoreCase()).isTrue();
    assertThat(name.value()).isEqualTo("café");
    assertThat(description.ignoreCase()).isTrue();
    assertThat(sku.ignoreCase()).isFalse();
    assertThat(or.logicalOperator()).isEqualTo(LogicalOperator.OR);
    assertThat(plan.allowedFieldsPolicy()).isSameAs(POLICY);
  }

  @Test
  void equalityOnTextIsLeftAlone() {
    QueryPlan<Item> exact =
        SpecificationQueryBuilder.forEntity(Item.class)
            .where("name", Operators.EQUALS, "Café")
            .build();

    QueryPlan<Item> plan = QueryPlans.ignoringCase(exact, Set.of("name"));

    assertThat(((PredicateCondition) plan.rootCondition().conditions().getFirst()).ignoreCase())
        .isFalse();
  }

  @Test
  void groupingKeepsConditionsButDropsSortAndFetches() {
    QueryPlan<Item> plan =
        QueryPlans.grouping(
            QueryPlans.fetching(clientPlan(), "seller"), List.of("status"), List.of());

    assertThat(plan.rootCondition()).isEqualTo(clientPlan().rootCondition());
    assertThat(plan.fetches()).isEmpty();
    assertThat(plan.sort()).isEqualTo(Sort.unsorted());
    assertThat(plan.groupBy()).containsExactly("status");
  }

  @Test
  void withoutRemovesTheTopLevelConditionsOnAField() {
    QueryPlan<Item> plan =
        SpecificationQueryBuilder.forEntity(Item.class)
            .where("seller.id", Operators.IN, List.of("seller-ana"))
            .where("status", Operators.EQUALS, "ACTIVE")
            .or(
                g ->
                    g.where("seller.id", Operators.EQUALS, "x")
                        .where("name", Operators.EQUALS, "y"))
            .allowedFields(POLICY)
            .build();

    QueryPlan<Item> facetPlan = QueryPlans.without(plan, "seller.id");

    assertThat(facetPlan.rootCondition().conditions()).hasSize(2);
    assertThat(((PredicateCondition) facetPlan.rootCondition().conditions().getFirst()).field())
        .isEqualTo("status");
    // Alternatives are kept whole: removing one branch would change their meaning.
    assertThat(facetPlan.rootCondition().conditions().get(1)).isInstanceOf(GroupCondition.class);
    assertThat(facetPlan.allowedFieldsPolicy()).isSameAs(POLICY);
  }

  @Test
  void theDefaultSortOnlyAppliesWhenTheClientDidNotSort() {
    QueryPlan<Item> unsorted = SpecificationQueryBuilder.forEntity(Item.class).build();
    QueryPlan<Item> sorted =
        SpecificationQueryBuilder.forEntity(Item.class).sort(Sort.by("name")).build();

    assertThat(QueryPlans.sortedByDefault(unsorted, Sort.by("status")).sort())
        .isEqualTo(Sort.by("status"));
    assertThat(QueryPlans.sortedByDefault(sorted, Sort.by("status"))).isSameAs(sorted);
  }

  @Test
  void groupingCanKeepOnlySomeGroups() {
    HavingCondition atLeastTwo =
        new HavingCondition(AggregateFunction.SUM, "quantity", Operators.GREATER_THAN_OR_EQUAL, 2);

    QueryPlan<Item> plan =
        QueryPlans.grouping(
            clientPlan(),
            List.of("name"),
            List.of(new AggregateSelection(AggregateFunction.SUM, "quantity", "units")),
            List.of(atLeastTwo));

    assertThat(plan.groupBy()).containsExactly("name");
    assertThat(plan.having()).containsExactly(atLeastTwo);
    assertThat(QueryPlans.grouping(clientPlan(), List.of("name"), List.of()).having()).isEmpty();
  }

  record ItemName(String name, String sku) {}

  @Test
  void projectingReadsTheChosenFieldsIntoTheType() {
    QueryPlan<Item> plan =
        QueryPlans.projecting(
            QueryPlans.fetching(clientPlan(), "seller"), ItemName.class, "name", "sku");

    assertThat(plan.projectionType()).isEqualTo(ItemName.class);
    assertThat(plan.projections()).containsExactly("name", "sku");
    assertThat(plan.selections())
        .containsExactly(new FieldSelection("name"), new FieldSelection("sku"));
    assertThat(plan.fetches()).isEmpty();
    assertThat(plan.rootCondition()).isEqualTo(clientPlan().rootCondition());
    assertThat(plan.sort()).isEqualTo(Sort.by("name"));
    assertThat(plan.allowedFieldsPolicy()).isEqualTo(POLICY);
  }

  @Test
  void aProjectionNeedsFields() {
    assertThatThrownBy(() -> QueryPlans.projecting(clientPlan(), ItemName.class))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
