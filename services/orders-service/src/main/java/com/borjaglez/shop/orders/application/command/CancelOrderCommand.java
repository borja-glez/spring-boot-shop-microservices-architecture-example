package com.borjaglez.shop.orders.application.command;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.borjaglez.cqrs.command.Command;

import lombok.Getter;

/** The customer cancels one of their orders. */
@Getter
public class CancelOrderCommand extends Command {

  @NotNull private final UUID orderId;
  @NotBlank private final String customerId;

  @Size(max = 200)
  private final String reason;

  public CancelOrderCommand(UUID orderId, String customerId, String reason) {
    this.orderId = orderId;
    this.customerId = customerId;
    this.reason = reason;
  }
}
