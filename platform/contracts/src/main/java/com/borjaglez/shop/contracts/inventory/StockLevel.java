package com.borjaglez.shop.contracts.inventory;

import java.util.UUID;

/**
 * Free stock of a product.
 *
 * @param productId catalog product id
 * @param available units on hand that no order holds
 */
public record StockLevel(UUID productId, int available) {}
