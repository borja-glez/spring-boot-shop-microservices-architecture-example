package com.borjaglez.shop.catalog.application.port.in;

/** Public data about a seller. The email is private and never exposed. */
public record SellerSummary(String id, String displayName, String city) {}
