package com.borjaglez.shop.catalog.application.fakes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.borjaglez.shop.catalog.application.port.out.Categories;
import com.borjaglez.shop.catalog.domain.CategoryId;

/** [Hexagonal · adaptador driven] The categories in a map, for the unit tests of the use cases. */
public final class InMemoryCategories implements Categories {

  private final Map<String, CategoryId> categories = new LinkedHashMap<>();

  @Override
  public Map<String, CategoryId> findIdsBySlugs(Set<String> slugs) {
    return categories.entrySet().stream()
        .filter(category -> slugs.contains(category.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /** Registers a category and returns its id. */
  public CategoryId add(String slug) {
    CategoryId id = new CategoryId(UUID.randomUUID());
    categories.put(slug, id);
    return id;
  }
}
