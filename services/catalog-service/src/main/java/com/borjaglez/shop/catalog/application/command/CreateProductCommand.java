package com.borjaglez.shop.catalog.application.command;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.borjaglez.cqrs.command.Command;

import lombok.Getter;

/**
 * A seller registers a new product as a draft. Answered with the new product id.
 *
 * <p>Bean Validation runs in the command bus middleware before the handler; business rules run in
 * the domain model.
 */
@Getter
public class CreateProductCommand extends Command {

  @NotBlank private final String sellerId;

  @NotBlank
  @Size(max = 40)
  private final String sku;

  @NotBlank
  @Size(max = 160)
  private final String name;

  @Size(max = 4000)
  private final String description;

  @NotNull
  @Positive
  @Digits(integer = 10, fraction = 2)
  private final BigDecimal price;

  @NotBlank
  @Size(min = 3, max = 3)
  private final String currency;

  @Size(max = 5)
  private final Set<String> categorySlugs;

  @Size(max = 10)
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
    this.sellerId = sellerId;
    this.sku = sku;
    this.name = name;
    this.description = description;
    this.price = price;
    this.currency = currency;
    this.categorySlugs = categorySlugs == null ? Set.of() : Set.copyOf(categorySlugs);
    this.tags = tags == null ? Set.of() : Set.copyOf(tags);
  }
}
