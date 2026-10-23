package com.borjaglez.shop.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.shop.catalog.FakeStockLevels;
import com.borjaglez.shop.catalog.domain.ProductEntity;
import com.borjaglez.shop.catalog.domain.ProductJpaRepository;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.core.SpecificationQueryBuilder;

/**
 * Query semantics of the catalog repository on PostgreSQL: joins combined with fetches, counts over
 * collection filters, and empty and null values.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class,
  FakeStockLevels.class
})
class ProductQueryIT {

  @Autowired ProductJpaRepository products;

  @Test
  void aLeftJoinAndALeftFetchOfTheSamePathStillFetch() {
    List<ProductEntity> found =
        products
            .query()
            .leftJoin("seller")
            .leftFetch("seller")
            .where("sku", Operators.EQUALS, "CAF-001")
            .findAll();

    assertThat(found)
        .singleElement()
        .satisfies(p -> assertThat(Hibernate.isInitialized(p.getSeller())).isTrue());
  }

  @Test
  void countMatchesTheRowsOfAFilterOnACollection() {
    QueryPlan<ProductEntity> plan =
        SpecificationQueryBuilder.forEntity(ProductEntity.class)
            .where(
                "categories.slug",
                Operators.IN,
                List.of(
                    "cafe-e-infusiones", "despensa", "hogar", "aceites-y-vinagres", "conservas"))
            .build();

    long count = products.count(plan);
    List<ProductEntity> rows = products.findAll(plan);

    assertThat(count).isEqualTo(rows.stream().map(ProductEntity::getId).distinct().count());
    assertThat(rows).doesNotHaveDuplicates();
  }

  @Test
  void inWithAnEmptyListMatchesNothing() {
    assertThat(products.query().where("sku", Operators.IN, List.of()).findAll()).isEmpty();
  }

  @Test
  void equalsNullMeansIsNull() {
    List<ProductEntity> isNull =
        products.query().where("publishedAt", Operators.IS_NULL, null).findAll();

    assertThat(products.query().where("publishedAt", Operators.EQUALS, null).findAll())
        .extracting(ProductEntity::getId)
        .containsExactlyInAnyOrderElementsOf(isNull.stream().map(ProductEntity::getId).toList());
  }
}
