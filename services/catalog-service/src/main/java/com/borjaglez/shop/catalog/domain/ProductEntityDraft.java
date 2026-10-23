package com.borjaglez.shop.catalog.domain;

import java.util.Set;

/** Everything a seller provides to create a product. Validated by {@link ProductEntity#draft}. */
public record ProductEntityDraft(
    SellerEntity seller,
    String sku,
    String name,
    String description,
    MoneyEmbeddable price,
    Set<CategoryEntity> categories,
    Set<String> tags) {}
