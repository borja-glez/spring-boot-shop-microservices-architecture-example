package com.borjaglez.shop.catalog.domain;

/**
 * [DDD · Value Object] Reference data of a merchant that sells products. Its id is the user id sent
 * by the client.
 */
public record Seller(SellerId id, String displayName, String city) {}
