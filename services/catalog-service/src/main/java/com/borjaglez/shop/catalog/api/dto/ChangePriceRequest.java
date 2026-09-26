package com.borjaglez.shop.catalog.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/catalog/products/{id}/price}. */
public record ChangePriceRequest(
    @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal price,
    @NotBlank @Size(min = 3, max = 3) String currency) {}
