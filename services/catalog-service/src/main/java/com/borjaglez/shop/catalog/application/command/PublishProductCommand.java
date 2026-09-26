package com.borjaglez.shop.catalog.application.command;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.borjaglez.cqrs.command.Command;

import lombok.Getter;

/** A seller puts one of their drafts on sale. */
@Getter
public class PublishProductCommand extends Command {

  @NotNull private final UUID productId;
  @NotBlank private final String sellerId;

  public PublishProductCommand(UUID productId, String sellerId) {
    this.productId = productId;
    this.sellerId = sellerId;
  }
}
