package com.borjaglez.shop.catalog.application.port.in;

/** One refinement option and how many products match it. */
public record FacetValue(String value, String label, long count) {}
