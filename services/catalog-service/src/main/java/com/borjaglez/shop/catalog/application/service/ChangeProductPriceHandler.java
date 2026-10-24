package com.borjaglez.shop.catalog.application.service;

import java.time.Clock;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.shop.catalog.application.port.in.ChangeProductPriceCommand;
import com.borjaglez.shop.catalog.domain.Money;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.ProductRepository;

/**
 * [DDD · Application Service] Use case "change the price of a product". The product is looked up
 * before the new price is checked, so a product of another seller is reported as missing whatever
 * the price.
 */
@CommandHandler(remote = false)
public class ChangeProductPriceHandler {

  private final ProductRepository products;
  private final Clock clock;

  public ChangeProductPriceHandler(ProductRepository products, Clock clock) {
    this.products = products;
    this.clock = clock;
  }

  @HandleCommand
  public void handle(ChangeProductPriceCommand command) {
    Product product = OwnedProducts.find(products, command.getProductId(), command.getSellerId());
    product.changePrice(new Money(command.getPrice(), command.getCurrency()), clock);
    products.save(product);
  }
}
