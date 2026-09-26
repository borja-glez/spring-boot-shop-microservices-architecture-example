package com.borjaglez.shop.inventory.application.command;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.borjaglez.cqrs.command.Command;

import lombok.Getter;

/** A warehouse worker counted a product and sets its units on hand. */
@Getter
public class AdjustStockCommand extends Command {

  @NotNull private final UUID productId;

  @Min(0)
  @Max(100_000)
  private final int onHand;

  @NotBlank private final String adjustedBy;

  public AdjustStockCommand(UUID productId, int onHand, String adjustedBy) {
    this.productId = productId;
    this.onHand = onHand;
    this.adjustedBy = adjustedBy;
  }
}
