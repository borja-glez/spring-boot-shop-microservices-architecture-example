package com.borjaglez.shop.contracts.notifications;

import java.util.List;

/**
 * Answer to {@link GetOrderNotices}.
 *
 * @param notices oldest first; empty when none was sent yet
 */
public record OrderNotices(List<OrderNotice> notices) {}
