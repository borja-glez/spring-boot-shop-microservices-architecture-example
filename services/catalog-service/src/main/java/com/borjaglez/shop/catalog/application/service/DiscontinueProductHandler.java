package com.borjaglez.shop.catalog.application.service;

import java.time.Clock;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.shop.catalog.application.port.in.DiscontinueProductCommand;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.ProductRepository;

/**
 * [DDD · Application Service] Use case "discontinue a product": a seller withdraws it for good.
 * Withdrawing a product that was on sale is announced; withdrawing a draft is not.
 */
@CommandHandler(remote = false)
public class DiscontinueProductHandler {

  private final ProductRepository products;
  private final Clock clock;

  public DiscontinueProductHandler(ProductRepository products, Clock clock) {
    this.products = products;
    this.clock = clock;
  }

  @HandleCommand
  public void handle(DiscontinueProductCommand command) {
    Product product = OwnedProducts.find(products, command.getProductId(), command.getSellerId());
    product.discontinue(command.getReason(), clock);
    products.save(product);
  }
}
