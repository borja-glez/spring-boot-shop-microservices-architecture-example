package com.borjaglez.shop.catalog.api.dto;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/catalog/products}. */
public record CreateProductRequest(
    @NotBlank @Size(max = 40) String sku,
    @NotBlank @Size(max = 160) String name,
    @Size(max = 4000) String description,
    @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal price,
    @NotBlank @Size(min = 3, max = 3) String currency,
    @Size(max = 5) Set<String> categories,
    @Size(max = 10) Set<String> tags) {}
