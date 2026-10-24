package com.borjaglez.shop.catalog.application.port.out;

import com.borjaglez.shop.catalog.domain.SellerId;

/**
 * A merchant that sells products, as {@link Sellers} finds it. Its id is the user id sent by the
 * client.
 */
public record Seller(SellerId id, String displayName, String city) {}
