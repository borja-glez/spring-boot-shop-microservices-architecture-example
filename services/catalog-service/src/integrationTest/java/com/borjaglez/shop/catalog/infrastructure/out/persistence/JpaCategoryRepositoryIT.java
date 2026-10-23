package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.borjaglez.shop.catalog.domain.CategoryId;
import com.borjaglez.shop.catalog.domain.CategoryRepository;

/** Categories of the seed (V2__catalog_seed.sql), looked up by slug. */
@CatalogPersistenceTest
class JpaCategoryRepositoryIT {

  @Autowired CategoryRepository categories;
  @Autowired JdbcTemplate jdbc;

  private CategoryId idOf(String slug) {
    return new CategoryId(
        jdbc.queryForObject("select id from category where slug = ?", UUID.class, slug));
  }

  @Test
  void findIdsBySlugsIgnoresUnknown() {
    Map<String, CategoryId> found = categories.findIdsBySlugs(Set.of("vinos", "libros", "nada"));

    assertThat(found)
        .containsOnly(Map.entry("vinos", idOf("vinos")), Map.entry("libros", idOf("libros")));
  }

  @Test
  void noSlugsFindNothing() {
    assertThat(categories.findIdsBySlugs(Set.of())).isEmpty();
  }
}
