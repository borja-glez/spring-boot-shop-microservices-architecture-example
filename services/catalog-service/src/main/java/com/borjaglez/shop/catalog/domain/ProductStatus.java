package com.borjaglez.shop.catalog.domain;

/** Lifecycle of a product. Only {@link #ACTIVE} products can be bought. */
public enum ProductStatus {
  /** Being prepared by the seller; invisible to shoppers. */
  DRAFT,
  /** Published and for sale. */
  ACTIVE,
  /** Withdrawn for good. Terminal. */
  DISCONTINUED
}
