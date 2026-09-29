package com.borjaglez.shop.contracts.inventory;

import java.util.List;

/**
 * Answer to {@link GetStockLevels}.
 *
 * @param levels one per product the inventory knows; unknown products are left out
 */
public record StockLevels(List<StockLevel> levels) {}
