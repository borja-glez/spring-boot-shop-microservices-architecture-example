package com.borjaglez.shop.catalog.domain;

import java.util.Set;

/** Everything a seller provides to create a product. Validated by {@link Product#draft}. */
public record ProductDraft(
    Seller seller,
    String sku,
    String name,
    String description,
    Money price,
    Set<Category> categories,
    Set<String> tags) {}
