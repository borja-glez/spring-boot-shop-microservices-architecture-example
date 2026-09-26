package com.borjaglez.shop.contracts.inventory;

import java.util.UUID;

/**
 * Units of one product to reserve for an order.
 *
 * @param productId catalog product id
 * @param sku product reference, for messages and logs
 * @param quantity units to reserve
 */
public record ReservationLine(UUID productId, String sku, int quantity) {}
