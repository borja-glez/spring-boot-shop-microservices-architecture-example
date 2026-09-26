package com.borjaglez.shop.orders.application.checkout;

import java.math.BigDecimal;
import java.util.UUID;

import com.borjaglez.shop.contracts.payments.PaymentAuthorization;
import com.borjaglez.shop.contracts.payments.PaymentRefund;

/**
 * Payments as the checkout saga needs them. Any exception is a technical failure that the saga
 * retries; a declined card comes back as a {@link PaymentAuthorization}.
 */
public interface PaymentsGateway {

  PaymentAuthorization authorize(
      UUID orderId, String customerId, BigDecimal amount, String currency);

  PaymentRefund refund(UUID orderId);
}
