package com.borjaglez.shop.catalog.application.service;

import java.util.List;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.catalog.application.exception.ProductNotFound;
import com.borjaglez.shop.catalog.application.port.in.Availability;
import com.borjaglez.shop.catalog.application.port.in.GetProductQuery;
import com.borjaglez.shop.catalog.application.port.in.ProductDetail;
import com.borjaglez.shop.catalog.application.port.out.CatalogReadModel;
import com.borjaglez.shop.catalog.application.port.out.StockLevels;
import com.borjaglez.shop.catalog.domain.ProductId;
import com.borjaglez.shop.catalog.domain.ProductStatus;

/**
 * [DDD · Application Service] Use case "show a product page": the product as the catalog has it
 * and, while it is on sale, the stock the inventory reports.
 *
 * <p>The page is read first and the inventory asked afterwards, so no database connection waits for
 * its answer: queries run without a transaction of the bus ({@code cqrs.transactions.queries=none})
 * and the read side opens and closes its own. A slow or failing inventory leaves the page without
 * stock instead of failing it.
 */
@QueryHandler(remote = false)
public class GetProductHandler {

  private final CatalogReadModel catalog;
  private final StockLevels stock;

  public GetProductHandler(CatalogReadModel catalog, StockLevels stock) {
    this.catalog = catalog;
    this.stock = stock;
  }

  /**
   * @throws ProductNotFound if no product with a public page has that slug
   */
  @HandleQuery
  public ProductDetail handle(GetProductQuery query) {
    ProductDetail detail =
        catalog
            .publicProduct(query.getSlug())
            .orElseThrow(() -> ProductNotFound.publishedAs(query.getSlug()));
    if (detail.status() != ProductStatus.ACTIVE) {
      return detail;
    }
    ProductId product = new ProductId(detail.id());
    Availability availability =
        stock
            .available(List.of(product))
            .map(units -> Availability.of(units.getOrDefault(product, 0)))
            .orElseGet(Availability::unknown);
    return detail.withAvailability(availability);
  }
}
