package com.borjaglez.shop.catalog.application.command;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.borjaglez.cqrs.command.Command;

import lombok.Getter;

/** A seller changes the price of one of their products. */
@Getter
public class ChangeProductPriceCommand extends Command {

  @NotNull private final UUID productId;
  @NotBlank private final String sellerId;

  @NotNull
  @Positive
  @Digits(integer = 10, fraction = 2)
  private final BigDecimal price;

  @NotBlank
  @Size(min = 3, max = 3)
  private final String currency;

  public ChangeProductPriceCommand(
      UUID productId, String sellerId, BigDecimal price, String currency) {
    this.productId = productId;
    this.sellerId = sellerId;
    this.price = price;
    this.currency = currency;
  }
}
