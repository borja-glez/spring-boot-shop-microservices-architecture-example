package com.borjaglez.shop.catalog.application.port.in;

/** How much of a product is left, as the shopper sees it. */
public enum StockStatus {
  IN_STOCK,
  /** Few units left: the page says how many. */
  LOW_STOCK,
  OUT_OF_STOCK,
  /** The inventory did not answer in time; the page is shown without stock. */
  UNKNOWN
}
