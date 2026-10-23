package com.borjaglez.shop.catalog.domain;

import java.util.Map;
import java.util.Set;

/**
 * [DDD · Repository] [Hexagonal · puerto de salida] Resolves the identities of the browsing
 * categories.
 */
public interface CategoryRepository {

  /** Returns the ids of the categories whose slug is in {@code slugs}, keyed by slug. */
  Map<String, CategoryId> findIdsBySlugs(Set<String> slugs);
}
