package com.borjaglez.shop.catalog.application.command;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.borjaglez.cqrs.command.Command;

import lombok.Getter;

/** A seller withdraws one of their products for good. */
@Getter
public class DiscontinueProductCommand extends Command {

  @NotNull private final UUID productId;
  @NotBlank private final String sellerId;

  @NotBlank
  @Size(max = 200)
  private final String reason;

  public DiscontinueProductCommand(UUID productId, String sellerId, String reason) {
    this.productId = productId;
    this.sellerId = sellerId;
    this.reason = reason;
  }
}
