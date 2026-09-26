package com.borjaglez.shop.orders.application.checkout;

import java.util.List;
import java.util.UUID;

import com.borjaglez.shop.contracts.inventory.ReservationLine;
import com.borjaglez.shop.contracts.inventory.StockRelease;
import com.borjaglez.shop.contracts.inventory.StockReservation;

/**
 * The inventory as the checkout saga needs it. Any exception is a technical failure that the saga
 * retries; a shortage comes back as a {@link StockReservation}.
 */
public interface InventoryGateway {

  StockReservation reserve(UUID orderId, List<ReservationLine> lines);

  StockRelease release(UUID orderId);
}
