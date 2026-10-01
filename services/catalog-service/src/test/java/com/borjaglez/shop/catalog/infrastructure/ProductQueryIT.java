package com.borjaglez.shop.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.shop.catalog.FakeStockLevels;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.ProductRepository;
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

  @Autowired ProductRepository products;

  @Test
  void aLeftJoinAndALeftFetchOfTheSamePathStillFetch() {
    List<Product> found =
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
    QueryPlan<Product> plan =
        SpecificationQueryBuilder.forEntity(Product.class)
            .where(
                "categories.slug",
                Operators.IN,
                List.of(
                    "cafe-e-infusiones", "despensa", "hogar", "aceites-y-vinagres", "conservas"))
            .build();

    long count = products.count(plan);
    List<Product> rows = products.findAll(plan);

    assertThat(count).isEqualTo(rows.stream().map(Product::getId).distinct().count());
    assertThat(rows).doesNotHaveDuplicates();
  }

  @Test
  void inWithAnEmptyListMatchesNothing() {
    assertThat(products.query().where("sku", Operators.IN, List.of()).findAll()).isEmpty();
  }

  @Test
  void equalsNullMeansIsNull() {
    List<Product> isNull = products.query().where("publishedAt", Operators.IS_NULL, null).findAll();

    assertThat(products.query().where("publishedAt", Operators.EQUALS, null).findAll())
        .extracting(Product::getId)
        .containsExactlyInAnyOrderElementsOf(isNull.stream().map(Product::getId).toList());
  }
}
