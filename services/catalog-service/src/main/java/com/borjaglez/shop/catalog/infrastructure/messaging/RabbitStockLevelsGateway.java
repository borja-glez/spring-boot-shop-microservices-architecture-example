package com.borjaglez.shop.catalog.infrastructure.messaging;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;

import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.rabbitmq.RemoteHandlerException;
import com.borjaglez.cqrs.rabbitmq.RemoteReplyTimeoutException;
import com.borjaglez.shop.catalog.application.query.StockLevelsGateway;
import com.borjaglez.shop.contracts.inventory.GetStockLevels;
import com.borjaglez.shop.contracts.inventory.StockLevel;
import com.borjaglez.shop.contracts.inventory.StockLevels;

/**
 * Asks the inventory service for stock over RabbitMQ and waits for the reply. A slow, failing or
 * unreachable inventory leaves the page without stock instead of failing it.
 */
class RabbitStockLevelsGateway implements StockLevelsGateway {

  private static final Logger log = LoggerFactory.getLogger(RabbitStockLevelsGateway.class);

  private final QueryBus remoteQueries;

  RabbitStockLevelsGateway(QueryBus remoteQueries) {
    this.remoteQueries = remoteQueries;
  }

  @Override
  public Optional<Map<UUID, Integer>> available(List<UUID> productIds) {
    try {
      StockLevels answer = remoteQueries.ask(new GetStockLevels(productIds));
      return Optional.of(
          answer.levels().stream()
              .collect(Collectors.toMap(StockLevel::productId, StockLevel::available)));
    } catch (RemoteReplyTimeoutException | RemoteHandlerException | AmqpException e) {
      log.warn("Stock levels unavailable: {}", e.toString());
      return Optional.empty();
    }
  }
}
