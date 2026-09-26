package com.borjaglez.shop.contracts.payments;

import java.util.UUID;

/**
 * Answer to {@link AuthorizePayment}.
 *
 * @param authorized whether the amount is authorized
 * @param paymentId id of the payment, also when declined
 * @param reason why it was declined, as a stable code; {@code null} when authorized
 */
public record PaymentAuthorization(boolean authorized, UUID paymentId, String reason) {}
