package com.borjaglez.shop.catalog.application.port.in;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

import com.borjaglez.cqrs.command.Command;

/**
 * [Hexagonal · puerto de entrada] A seller registers a new product as a draft. Answered with the id
 * of the new product.
 *
 * <p>The seller is the user who sends the request. The other fields arrive as the seller typed
 * them; the use case and the domain model check them.
 */
public final class CreateProductCommand extends Command {

  private final String sellerId;
  private final String sku;
  private final String name;
  private final String description;
  private final BigDecimal price;
  private final String currency;
  private final Set<String> categorySlugs;
  private final Set<String> tags;

  public CreateProductCommand(
      String sellerId,
      String sku,
      String name,
      String description,
      BigDecimal price,
      String currency,
      Set<String> categorySlugs,
      Set<String> tags) {
    this.sellerId = Objects.requireNonNull(sellerId, "sellerId must not be null");
    this.sku = sku;
    this.name = name;
    this.description = description;
    this.price = price;
    this.currency = currency;
    this.categorySlugs = categorySlugs == null ? Set.of() : Set.copyOf(categorySlugs);
    this.tags = tags == null ? Set.of() : Set.copyOf(tags);
  }

  public String getSellerId() {
    return sellerId;
  }

  public String getSku() {
    return sku;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public String getCurrency() {
    return currency;
  }

  public Set<String> getCategorySlugs() {
    return categorySlugs;
  }

  public Set<String> getTags() {
    return tags;
  }
}
