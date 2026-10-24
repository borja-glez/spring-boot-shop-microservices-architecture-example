package com.borjaglez.shop.catalog.application.port.in;

import java.util.Objects;
import java.util.UUID;

import com.borjaglez.cqrs.command.Command;

/** [Hexagonal · puerto de entrada] A seller puts one of their drafts on sale. */
public final class PublishProductCommand extends Command {

  private final UUID productId;
  private final String sellerId;

  public PublishProductCommand(UUID productId, String sellerId) {
    this.productId = Objects.requireNonNull(productId, "productId must not be null");
    this.sellerId = Objects.requireNonNull(sellerId, "sellerId must not be null");
  }

  public UUID getProductId() {
    return productId;
  }

  public String getSellerId() {
    return sellerId;
  }
}
