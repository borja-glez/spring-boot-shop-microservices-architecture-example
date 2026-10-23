package com.borjaglez.shop.catalog.application.service;

import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.shop.catalog.application.exception.DuplicateSkuException;
import com.borjaglez.shop.catalog.application.exception.SellerNotFoundException;
import com.borjaglez.shop.catalog.application.exception.UnknownCategoryException;
import com.borjaglez.shop.catalog.application.port.in.CreateProductCommand;
import com.borjaglez.shop.catalog.application.port.out.Categories;
import com.borjaglez.shop.catalog.application.port.out.Sellers;
import com.borjaglez.shop.catalog.domain.CategoryId;
import com.borjaglez.shop.catalog.domain.Money;
import com.borjaglez.shop.catalog.domain.Product;
import com.borjaglez.shop.catalog.domain.ProductDraft;
import com.borjaglez.shop.catalog.domain.ProductRepository;
import com.borjaglez.shop.catalog.domain.SellerId;
import com.borjaglez.shop.catalog.domain.Sku;

/**
 * [DDD · Application Service] Use case "create a product": a seller registers a draft, which
 * announces nothing until it is published.
 *
 * <p>A request that breaks several rules always reports the same one, because they are checked in a
 * fixed order: the seller, the price, the categories, the SKU, the name, the description, the tags,
 * and last, once the draft is valid, whether another product already uses the SKU.
 */
@CommandHandler(remote = false)
public class CreateProductHandler {

  private final ProductRepository products;
  private final Sellers sellers;
  private final Categories categories;
  private final Clock clock;

  public CreateProductHandler(
      ProductRepository products, Sellers sellers, Categories categories, Clock clock) {
    this.products = products;
    this.sellers = sellers;
    this.categories = categories;
    this.clock = clock;
  }

  @HandleCommand
  public UUID handle(CreateProductCommand command) {
    SellerId seller = existingSeller(command.getSellerId());
    Money price = new Money(command.getPrice(), command.getCurrency());
    Set<CategoryId> productCategories = knownCategories(command.getCategorySlugs());
    Sku sku = new Sku(command.getSku());
    Product product =
        Product.draft(
            new ProductDraft(
                seller,
                sku,
                command.getName(),
                command.getDescription(),
                price,
                productCategories,
                command.getTags()),
            clock);
    if (products.existsBySku(sku)) {
      throw new DuplicateSkuException(sku);
    }
    products.save(product);
    return product.getId().value();
  }

  /** The seller is the user of the request, whose id the inbound adapter has already checked. */
  private SellerId existingSeller(String sellerId) {
    return sellers
        .findById(new SellerId(sellerId))
        .orElseThrow(() -> new SellerNotFoundException(sellerId))
        .id();
  }

  private Set<CategoryId> knownCategories(Set<String> slugs) {
    Map<String, CategoryId> found = categories.findIdsBySlugs(slugs);
    if (found.size() != slugs.size()) {
      Set<String> unknown = new HashSet<>(slugs);
      unknown.removeAll(found.keySet());
      throw new UnknownCategoryException(unknown);
    }
    return Set.copyOf(found.values());
  }
}
