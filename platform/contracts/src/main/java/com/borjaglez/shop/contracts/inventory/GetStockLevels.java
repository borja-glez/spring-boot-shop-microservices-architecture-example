package com.borjaglez.shop.contracts.inventory;

import java.util.List;
import java.util.UUID;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Free units of some products, right now. Answered with {@link StockLevels}. A read over RabbitMQ
 * instead of a projection: stock changes with every checkout, so a copy kept from events would be
 * behind exactly when it matters.
 */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "inventory", module = "stock", name = "get-stock-levels")
public class GetStockLevels extends Query {

  private List<UUID> productIds;
}
