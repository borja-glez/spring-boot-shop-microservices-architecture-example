package com.borjaglez.shop.contracts.inventory;

import java.util.UUID;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** Gives back the stock reserved for an order. Answered with a {@link StockRelease}. Idempotent. */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "inventory", module = "stock", name = "release-stock")
public class ReleaseStock extends Command {

  private UUID orderId;
}
