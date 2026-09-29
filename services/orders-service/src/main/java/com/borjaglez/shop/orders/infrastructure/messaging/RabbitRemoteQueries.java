package com.borjaglez.shop.orders.infrastructure.messaging;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;

import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.rabbitmq.RemoteHandlerException;
import com.borjaglez.cqrs.rabbitmq.RemoteReplyTimeoutException;
import com.borjaglez.shop.contracts.inventory.GetStockLevels;
import com.borjaglez.shop.contracts.inventory.StockLevel;
import com.borjaglez.shop.contracts.inventory.StockLevels;
import com.borjaglez.shop.contracts.notifications.GetOrderNotices;
import com.borjaglez.shop.contracts.notifications.OrderNotice;
import com.borjaglez.shop.contracts.notifications.OrderNotices;
import com.borjaglez.shop.orders.application.query.OrderNoticesGateway;
import com.borjaglez.shop.orders.application.query.StockLevelsGateway;

/**
 * Reads from inventory and notifications, as RabbitMQ request and reply. A slow, failing or
 * unreachable service leaves its part of the answer empty instead of failing the request.
 */
class RabbitRemoteQueries implements StockLevelsGateway, OrderNoticesGateway {

  private static final Logger log = LoggerFactory.getLogger(RabbitRemoteQueries.class);

  private final QueryBus remoteQueries;

  RabbitRemoteQueries(QueryBus remoteQueries) {
    this.remoteQueries = remoteQueries;
  }

  @Override
  public Optional<Map<UUID, Integer>> available(List<UUID> productIds) {
    return ask(
        "GetStockLevels",
        () -> {
          StockLevels answer = remoteQueries.ask(new GetStockLevels(productIds));
          return answer.levels().stream()
              .collect(Collectors.toMap(StockLevel::productId, StockLevel::available));
        });
  }

  @Override
  public Optional<List<OrderNotice>> notices(UUID orderId, String customerId) {
    return ask(
        "GetOrderNotices",
        () -> {
          OrderNotices answer = remoteQueries.ask(new GetOrderNotices(orderId, customerId));
          return answer.notices();
        });
  }

  private static <T> Optional<T> ask(String query, Supplier<T> call) {
    try {
      return Optional.of(call.get());
    } catch (RemoteReplyTimeoutException | RemoteHandlerException | AmqpException e) {
      log.warn("{} got no answer: {}", query, e.toString());
      return Optional.empty();
    }
  }
}
