package com.borjaglez.shop.orders.application.command;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.borjaglez.cqrs.command.Command;

import lombok.Getter;

/** A customer orders products from their cart. Answered with the new order id. */
@Getter
public class PlaceOrderCommand extends Command {

  @NotBlank private final String customerId;

  @NotEmpty
  @Size(max = 20)
  private final List<@Valid Item> items;

  public PlaceOrderCommand(String customerId, List<Item> items) {
    this.customerId = customerId;
    this.items = items == null ? List.of() : List.copyOf(items);
  }

  /** A product and how many units of it. */
  public record Item(@NotNull UUID productId, @Min(1) @Max(99) int quantity) {}
}
