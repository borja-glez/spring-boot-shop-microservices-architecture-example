package com.borjaglez.shop.orders.api.dto;

import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/orders/{id}/cancel}. The reason is optional. */
public record CancelOrderRequest(@Size(max = 200) String reason) {}
