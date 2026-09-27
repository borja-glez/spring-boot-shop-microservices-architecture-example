package com.borjaglez.shop.support.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.borjaglez.specrepository.core.AllowedFieldsPolicy;
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
  void withoutKeepsTheServerConditions() {
    QueryPlan<Item> scoped =
        clientPlan().toBuilder().where("seller.id", Operators.EQUALS, "seller-ana").build();

    QueryPlan<Item> facetPlan = QueryPlans.without(scoped, "seller.id");

    assertThat(facetPlan.serverCondition()).isEqualTo(scoped.serverCondition());
    assertThat(facetPlan.serverCondition().conditions()).hasSize(1);
  }
}
