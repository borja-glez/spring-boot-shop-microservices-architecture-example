package com.borjaglez.shop.catalog.application.service;

import java.time.Clock;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.shop.catalog.application.port.in.PublishProductCommand;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.ProductRepository;

/**
 * [DDD · Application Service] Use case "publish a product": a seller puts one of their drafts on
 * sale, which other services hear about.
 */
@CommandHandler(remote = false)
public class PublishProductHandler {

  private final ProductRepository products;
  private final Clock clock;

  public PublishProductHandler(ProductRepository products, Clock clock) {
    this.products = products;
    this.clock = clock;
  }

  @HandleCommand
  public void handle(PublishProductCommand command) {
    Product product = OwnedProducts.find(products, command.getProductId(), command.getSellerId());
    product.publish(clock);
    products.save(product);
  }
}
