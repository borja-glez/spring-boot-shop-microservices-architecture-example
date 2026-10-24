package com.borjaglez.shop.catalog.application.port.in;

import java.math.BigDecimal;

/**
 * Cheapest and most expensive price among the matching products; both {@code null} when nothing
 * matches.
 */
public record PriceRange(BigDecimal min, BigDecimal max) {}
