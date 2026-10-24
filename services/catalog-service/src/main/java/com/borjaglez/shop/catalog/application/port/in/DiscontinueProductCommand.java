package com.borjaglez.shop.catalog.application.port.in;

import java.util.Objects;
import java.util.UUID;

import com.borjaglez.cqrs.command.Command;

/** [Hexagonal · puerto de entrada] A seller withdraws one of their products for good. */
public final class DiscontinueProductCommand extends Command {

  private final UUID productId;
  private final String sellerId;
  private final String reason;

  public DiscontinueProductCommand(UUID productId, String sellerId, String reason) {
    this.productId = Objects.requireNonNull(productId, "productId must not be null");
    this.sellerId = Objects.requireNonNull(sellerId, "sellerId must not be null");
    this.reason = reason;
  }

  public UUID getProductId() {
    return productId;
  }

  public String getSellerId() {
    return sellerId;
  }

  public String getReason() {
    return reason;
  }
}
