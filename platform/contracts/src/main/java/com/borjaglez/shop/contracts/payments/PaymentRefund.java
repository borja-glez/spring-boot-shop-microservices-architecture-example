package com.borjaglez.shop.contracts.payments;

/**
 * Answer to {@link RefundPayment}.
 *
 * @param refunded whether an authorized payment existed and is now refunded; {@code false} when
 *     there was nothing to refund, which is not an error
 */
public record PaymentRefund(boolean refunded) {}
