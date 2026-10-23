package com.borjaglez.shop.catalog.domain;

/** [DDD · Value Object] Reference data of a browsing category. */
public record Category(CategoryId id, String slug, String name) {}
