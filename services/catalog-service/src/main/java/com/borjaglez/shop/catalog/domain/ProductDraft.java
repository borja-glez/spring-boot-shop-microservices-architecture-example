package com.borjaglez.shop.catalog.domain;

import java.util.Set;

/**
 * Everything a seller provides to create a product. Validated by {@link Product#draft}: the name,
 * the description and the tags arrive as the seller typed them.
 */
public record ProductDraft(
    SellerId seller,
    Sku sku,
    String name,
    String description,
    Money price,
    Set<CategoryId> categories,
    Set<String> tags) {}
