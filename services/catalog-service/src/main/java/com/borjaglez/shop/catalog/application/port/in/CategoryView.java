package com.borjaglez.shop.catalog.application.port.in;

/** A category with the number of products on sale in it. */
public record CategoryView(String slug, String name, long activeProducts) {}
