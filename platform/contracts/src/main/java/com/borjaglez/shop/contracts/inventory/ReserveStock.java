package com.borjaglez.shop.contracts.inventory;

import java.util.List;
import java.util.UUID;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Reserves stock for an order, all or nothing. Answered with a {@link StockReservation}.
 * Idempotent: asking again for the same order returns the first answer.
 */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "inventory", module = "stock", name = "reserve-stock")
public class ReserveStock extends Command {

  private UUID orderId;
  private List<ReservationLine> lines;
}
