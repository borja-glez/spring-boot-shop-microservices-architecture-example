package com.borjaglez.shop.orders.infrastructure.checkout;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.borjaglez.cqrs.rabbitmq.RabbitMqCommandBus;
import com.borjaglez.shop.contracts.inventory.ReleaseStock;
import com.borjaglez.shop.contracts.inventory.ReservationLine;
import com.borjaglez.shop.contracts.inventory.ReserveStock;
import com.borjaglez.shop.contracts.inventory.StockRelease;
import com.borjaglez.shop.contracts.inventory.StockReservation;
import com.borjaglez.shop.contracts.payments.AuthorizePayment;
import com.borjaglez.shop.contracts.payments.PaymentAuthorization;
import com.borjaglez.shop.contracts.payments.PaymentRefund;
import com.borjaglez.shop.contracts.payments.RefundPayment;
import com.borjaglez.shop.orders.application.checkout.InventoryGateway;
import com.borjaglez.shop.orders.application.checkout.PaymentsGateway;

/**
 * The checkout saga's calls to inventory and payments, as RabbitMQ request and reply. A missing
 * reply ({@code RemoteReplyTimeoutException}) or a failure in the other service ({@code
 * RemoteHandlerException}) reaches the saga as an exception, which it retries.
 */
class RabbitCheckoutGateways implements InventoryGateway, PaymentsGateway {

  private final RabbitMqCommandBus commands;

  RabbitCheckoutGateways(RabbitMqCommandBus commands) {
    this.commands = commands;
  }

  @Override
  public StockReservation reserve(UUID orderId, List<ReservationLine> lines) {
    return reply(commands.dispatchAndReceive(new ReserveStock(orderId, lines)), "ReserveStock");
  }

  @Override
  public StockRelease release(UUID orderId) {
    return reply(commands.dispatchAndReceive(new ReleaseStock(orderId)), "ReleaseStock");
  }

  @Override
  public PaymentAuthorization authorize(
      UUID orderId, String customerId, BigDecimal amount, String currency) {
    return reply(
        commands.dispatchAndReceive(new AuthorizePayment(orderId, customerId, amount, currency)),
        "AuthorizePayment");
  }

  @Override
  public PaymentRefund refund(UUID orderId) {
    return reply(commands.dispatchAndReceive(new RefundPayment(orderId)), "RefundPayment");
  }

  /** The handlers never answer {@code null}; if one does, the step is retried. */
  private static <T> T reply(T result, String command) {
    if (result == null) {
      throw new IllegalStateException(command + " was answered with nothing");
    }
    return result;
  }
}
