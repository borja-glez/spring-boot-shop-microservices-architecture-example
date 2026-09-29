package com.borjaglez.shop.orders.application.query;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.borjaglez.cqrs.query.Query;

import lombok.Getter;

/**
 * Prices a cart and checks its stock before the customer places it. Answered with a {@code
 * CartQuote}. Nothing is reserved: the checkout saga still decides.
 */
@Getter
public class QuoteCartQuery extends Query {

  private final List<Item> items;

  public QuoteCartQuery(List<Item> items) {
    this.items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
  }

  public record Item(UUID productId, int quantity) {}
}
