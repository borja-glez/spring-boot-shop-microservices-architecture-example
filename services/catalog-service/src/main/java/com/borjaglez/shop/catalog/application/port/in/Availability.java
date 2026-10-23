package com.borjaglez.shop.catalog.application.port.in;

/**
 * Stock of a product on sale.
 *
 * @param status what the page shows
 * @param units free units; {@code null} when unknown
 */
public record Availability(StockStatus status, Integer units) {

  /** Up to this many free units the stock counts as low. */
  public static final int LOW_STOCK_UNITS = 5;

  /** The stock of a product with this many free units; never below zero. */
  public static Availability of(int units) {
    StockStatus status =
        units <= 0
            ? StockStatus.OUT_OF_STOCK
            : units <= LOW_STOCK_UNITS ? StockStatus.LOW_STOCK : StockStatus.IN_STOCK;
    return new Availability(status, Math.max(units, 0));
  }

  /** The stock of a product the inventory did not report. */
  public static Availability unknown() {
    return new Availability(StockStatus.UNKNOWN, null);
  }
}
