package com.borjaglez.shop.catalog.application.port.in;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.borjaglez.cqrs.command.Command;

/** [Hexagonal · puerto de entrada] A seller changes the price of one of their products. */
public final class ChangeProductPriceCommand extends Command {

  private final UUID productId;
  private final String sellerId;
  private final BigDecimal price;
  private final String currency;

  public ChangeProductPriceCommand(
      UUID productId, String sellerId, BigDecimal price, String currency) {
    this.productId = Objects.requireNonNull(productId, "productId must not be null");
    this.sellerId = Objects.requireNonNull(sellerId, "sellerId must not be null");
    this.price = price;
    this.currency = currency;
  }

  public UUID getProductId() {
    return productId;
  }

  public String getSellerId() {
    return sellerId;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public String getCurrency() {
    return currency;
  }
}
