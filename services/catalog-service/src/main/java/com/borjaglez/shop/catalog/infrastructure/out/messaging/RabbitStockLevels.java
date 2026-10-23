package com.borjaglez.shop.catalog.infrastructure.out.messaging;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;

import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.rabbitmq.RemoteHandlerException;
import com.borjaglez.cqrs.rabbitmq.RemoteReplyTimeoutException;
import com.borjaglez.shop.catalog.application.port.out.StockLevels;
import com.borjaglez.shop.catalog.domain.ProductId;
import com.borjaglez.shop.contracts.inventory.GetStockLevels;
import com.borjaglez.shop.contracts.inventory.StockLevel;

/**
 * [Hexagonal · adaptador driven] [Context map · Customer/Supplier] Asks the inventory service for
 * stock over RabbitMQ, with the query of its published contract, and waits for the reply at most
 * {@code cqrs.rabbitmq.queries.reply-timeout} ({@code REMOTE_QUERY_TIMEOUT}). A slow, failing or
 * unreachable inventory leaves the page without stock instead of failing it.
 */
public class RabbitStockLevels implements StockLevels {

  private static final Logger log = LoggerFactory.getLogger(RabbitStockLevels.class);

  private final QueryBus remoteQueries;

  public RabbitStockLevels(QueryBus remoteQueries) {
    this.remoteQueries = remoteQueries;
  }

  @Override
  public Optional<Map<ProductId, Integer>> available(List<ProductId> products) {
    try {
      com.borjaglez.shop.contracts.inventory.StockLevels answer =
          remoteQueries.ask(new GetStockLevels(products.stream().map(ProductId::value).toList()));
      return Optional.of(
          answer.levels().stream()
              .collect(
                  Collectors.toMap(
                      level -> new ProductId(level.productId()), StockLevel::available)));
    } catch (RemoteReplyTimeoutException | RemoteHandlerException | AmqpException e) {
      log.warn("Stock levels unavailable: {}", e.toString());
      return Optional.empty();
    }
  }
}
