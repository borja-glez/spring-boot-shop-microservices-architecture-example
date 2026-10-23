package com.borjaglez.shop.catalog.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The stored form of a price. Mapped as an embeddable record so queries can reach {@code
 * price.amount} and {@code price.currency} through specification-repository paths; the rules of a
 * price live in {@link Money}.
 */
@Embeddable
public record MoneyEmbeddable(
    @Column(name = "price_amount", nullable = false, precision = 12, scale = 2) BigDecimal amount,
    @Column(name = "price_currency", nullable = false, length = 3) String currency) {}
