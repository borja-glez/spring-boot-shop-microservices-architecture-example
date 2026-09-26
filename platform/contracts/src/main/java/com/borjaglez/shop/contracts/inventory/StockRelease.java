package com.borjaglez.shop.contracts.inventory;

/**
 * Answer to {@link ReleaseStock}.
 *
 * @param released whether a reservation existed and is now released; {@code false} when there was
 *     nothing to release, which is not an error
 */
public record StockRelease(boolean released) {}
