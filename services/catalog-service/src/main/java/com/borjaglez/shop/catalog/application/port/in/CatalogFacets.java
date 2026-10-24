package com.borjaglez.shop.catalog.application.port.in;

import java.util.List;

/**
 * Counts that let shoppers refine a search. Each list holds the commonest values first, by count
 * and then by label.
 */
public record CatalogFacets(
    List<FacetValue> categories,
    List<FacetValue> sellers,
    List<FacetValue> tags,
    PriceRange price) {}
