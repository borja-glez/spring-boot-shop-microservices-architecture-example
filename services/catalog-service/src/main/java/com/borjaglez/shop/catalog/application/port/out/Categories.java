package com.borjaglez.shop.catalog.application.port.out;

import java.util.Map;
import java.util.Set;

import com.borjaglez.shop.catalog.domain.CategoryId;

/**
 * [Hexagonal · puerto de salida] Resolves the identities of the browsing categories: reference data
 * the use cases read, not an aggregate they change.
 */
public interface Categories {

  /** Returns the ids of the categories whose slug is in {@code slugs}, keyed by slug. */
  Map<String, CategoryId> findIdsBySlugs(Set<String> slugs);
}
