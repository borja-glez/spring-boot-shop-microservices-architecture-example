package com.borjaglez.shop.contracts.orders;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One product of an order, with the price it had when the order was placed.
 *
 * @param productId catalog product id
 * @param sku product reference
 * @param name product name at the time of the order
 * @param quantity units ordered
 * @param unitPrice price of one unit
 */
public record OrderLine(
    UUID productId, String sku, String name, int quantity, BigDecimal unitPrice) {}
