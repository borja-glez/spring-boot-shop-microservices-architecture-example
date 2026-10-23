package com.borjaglez.shop.catalog.infrastructure.out.persistence;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.borjaglez.shop.catalog.domain.CategoryEntity;
import com.borjaglez.shop.catalog.domain.CategoryId;
import com.borjaglez.shop.catalog.domain.CategoryJpaRepository;
import com.borjaglez.shop.catalog.domain.CategoryRepository;
import com.borjaglez.specrepository.core.Operators;

/** [Hexagonal · adaptador driven] Resolves categories from PostgreSQL by their slug. */
public class JpaCategoryRepository implements CategoryRepository {

  private final CategoryJpaRepository categories;

  public JpaCategoryRepository(CategoryJpaRepository categories) {
    this.categories = categories;
  }

  @Override
  public Map<String, CategoryId> findIdsBySlugs(Set<String> slugs) {
    if (slugs.isEmpty()) {
      return Map.of();
    }
    return categories.query().where("slug", Operators.IN, List.copyOf(slugs)).findAll().stream()
        .collect(
            Collectors.toMap(
                CategoryEntity::getSlug, category -> new CategoryId(category.getId())));
  }
}
