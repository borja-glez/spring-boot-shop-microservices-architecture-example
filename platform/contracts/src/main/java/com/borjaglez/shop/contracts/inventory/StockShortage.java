package com.borjaglez.shop.contracts.inventory;

import java.util.UUID;

/**
 * A product without enough free stock for a reservation.
 *
 * @param productId catalog product id
 * @param sku product reference
 * @param requested units the order asked for
 * @param available free units at the time of the request
 */
public record StockShortage(UUID productId, String sku, int requested, int available) {}
