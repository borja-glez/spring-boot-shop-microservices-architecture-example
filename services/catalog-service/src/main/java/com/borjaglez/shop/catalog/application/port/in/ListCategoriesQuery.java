package com.borjaglez.shop.catalog.application.port.in;

import com.borjaglez.cqrs.query.Query;

/**
 * [Hexagonal · puerto de entrada] Every category with its number of products on sale. Answered with
 * a {@code List<CategoryView>}.
 */
public final class ListCategoriesQuery extends Query {}
