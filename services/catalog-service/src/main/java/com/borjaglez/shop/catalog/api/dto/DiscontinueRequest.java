package com.borjaglez.shop.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/catalog/products/{id}/discontinue}. */
public record DiscontinueRequest(@NotBlank @Size(max = 200) String reason) {}
