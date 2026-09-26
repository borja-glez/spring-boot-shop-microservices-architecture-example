package com.borjaglez.shop.orders.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/orders}: the cart contents. Prices come from the catalog. */
public record PlaceOrderRequest(@NotEmpty @Size(max = 20) List<@Valid @NotNull Item> items) {

  public record Item(@NotNull UUID productId, @Min(1) @Max(99) int quantity) {}
}
