package com.borjaglez.shop.inventory.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PUT /api/inventory/stock/{productId}}: the units counted in the warehouse. */
public record AdjustStockRequest(@NotNull @Min(0) @Max(100_000) Integer onHand) {}
