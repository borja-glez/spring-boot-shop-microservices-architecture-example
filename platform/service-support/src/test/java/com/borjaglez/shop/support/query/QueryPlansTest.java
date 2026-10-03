package com.borjaglez.shop.support.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
import com.borjaglez.specrepository.core.DisallowedFieldException;
import com.borjaglez.specrepository.core.GroupCondition;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.SpecificationQueryBuilder;

class QueryPlansTest {

  /** Plans only need a type; nothing is queried. */
  static class Item {}

  private static final AllowedFieldsPolicy POLICY =
      AllowedFieldsPolicy.of(Set.of("seller.id", "status", "name"), Set.of("name"));

  private static QueryPlan<Item> clientPlan() {
    return SpecificationQueryBuilder.forEntity(Item.class)
        .where("seller.id", Operators.IN, List.of("seller-ana"))
        .where("status", Operators.EQUALS, "ACTIVE")
        .or(g -> g.where("seller.id", Operators.EQUALS, "x").where("name", Operators.EQUALS, "y"))
        .sort(Sort.by("name"))
        .allowedFields(POLICY)
        .build();
  }

  @Test
  void withoutRemovesTheTopLevelConditionsOnAField() {
    QueryPlan<Item> facetPlan = QueryPlans.without(clientPlan(), "seller.id");

    assertThat(facetPlan.rootCondition().conditions()).hasSize(2);
    assertThat(((PredicateCondition) facetPlan.rootCondition().conditions().getFirst()).field())
        .isEqualTo("status");
    // Alternatives are kept whole: removing one branch would change their meaning.
    assertThat(facetPlan.rootCondition().conditions().get(1)).isInstanceOf(GroupCondition.class);
    assertThat(facetPlan.sort()).isEqualTo(Sort.by("name"));
    assertThat(facetPlan.allowedFieldsPolicy()).isSameAs(POLICY);
  }

  @Test
  void withoutKeepsTheRemainingConditionsAsClientConditions() {
    QueryPlan<Item> facetPlan =
        QueryPlans.without(
            SpecificationQueryBuilder.forEntity(Item.class)
                .where("name", Operators.CONTAINS, "cafe", true, false)
                .where("seller.id", Operators.EQUALS, "seller-ana")
                .allowedFields(POLICY)
                .build(),
            "seller.id");

    assertThat(facetPlan.rootCondition().conditions())
        .containsExactly(new PredicateCondition("name", Operators.CONTAINS, "cafe", true, false));
    assertThat(facetPlan.serverCondition().conditions()).isEmpty();
    // Still client input: checked against the whitelist when the query runs.
    assertThatThrownBy(
            () ->
                POLICY.validate(
                    QueryPlans.without(
                        SpecificationQueryBuilder.forEntity(Item.class)
                            .where("price", Operators.EQUALS, 1)
                            .allowedFields(POLICY)
                            .build(),
                        "seller.id")))
        .isInstanceOf(DisallowedFieldException.class);
  }

  @Test
  void withoutCopiesNestedGroups() {
    QueryPlan<Item> plan =
        SpecificationQueryBuilder.forEntity(Item.class)
            .or(
                g ->
                    g.where("name", Operators.EQUALS, "x")
                        .and(
                            a ->
                                a.where("status", Operators.EQUALS, "A")
                                    .or(o -> o.where("name", Operators.IS_NULL, null))))
            .and(g -> g.where("status", Operators.NOT_EQUALS, "B"))
            .build();

    assertThat(QueryPlans.without(plan, "seller.id").rootCondition())
        .isEqualTo(plan.rootCondition());
  }

  @Test
  void withoutRejectsAPlanTheServerAlreadyExtended() {
    QueryPlan<Item> scoped =
        clientPlan().toBuilder().where("seller.id", Operators.EQUALS, "seller-ana").build();

    assertThatThrownBy(() -> QueryPlans.without(scoped, "seller.id"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Only a client plan");
    assertThatThrownBy(
            () -> QueryPlans.without(clientPlan().toBuilder().leftFetch("seller").build(), "name"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withoutRejectsSubqueries() {
    QueryPlan<Item> top =
        SpecificationQueryBuilder.forEntity(Item.class)
            .exists("tags", sub -> sub.where("value", Operators.EQUALS, "a"))
            .build();
    QueryPlan<Item> nested =
        SpecificationQueryBuilder.forEntity(Item.class)
            .or(g -> g.exists("tags", sub -> sub.where("value", Operators.EQUALS, "a")))
            .build();

    assertThatThrownBy(() -> QueryPlans.without(top, "name"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> QueryPlans.without(nested, "name"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
