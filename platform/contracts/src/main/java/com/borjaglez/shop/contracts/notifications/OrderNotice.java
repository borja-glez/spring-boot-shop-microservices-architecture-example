package com.borjaglez.shop.contracts.notifications;

import java.time.OffsetDateTime;

/**
 * A notice as the customer received it.
 *
 * @param kind what it tells: {@code ORDER_CONFIRMED}, {@code ORDER_REJECTED}, {@code
 *     ORDER_CANCELLED} or {@code PAYMENT_REFUNDED}
 * @param title short text
 * @param body full text
 * @param sentAt when the event behind it happened
 * @param read whether the customer opened it
 */
public record OrderNotice(
    String kind, String title, String body, OffsetDateTime sentAt, boolean read) {}
