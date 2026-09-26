package com.borjaglez.shop.contracts.inventory;

import java.util.List;

/**
 * Answer to {@link ReserveStock}. The reservation is all or nothing: when a product is short, none
 * is reserved and {@code shortages} says which ones.
 *
 * @param reserved whether the stock is now reserved for the order
 * @param shortages products without enough stock; empty when reserved
 */
public record StockReservation(boolean reserved, List<StockShortage> shortages) {}
