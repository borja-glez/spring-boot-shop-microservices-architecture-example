package com.borjaglez.shop.orders.application.query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.orders.application.query.OrderViews.CartQuote;
import com.borjaglez.shop.orders.application.query.OrderViews.QuoteLine;
import com.borjaglez.shop.orders.application.query.OrderViews.QuoteProblem;
import com.borjaglez.shop.orders.application.query.QuoteCartQuery.Item;
import com.borjaglez.shop.orders.domain.CatalogProduct;
import com.borjaglez.shop.orders.domain.CatalogProductRepository;
import com.borjaglez.specrepository.core.Operators;

/**
 * Quotes a cart from two sources: prices from the local catalog projection, which events keep up to
 * date, and stock from the inventory, asked over RabbitMQ because it changes with every checkout.
 * Not transactional, so no database connection waits for the inventory's answer.
 */
@QueryHandler
public class CartQuoteHandler {

  private final CatalogProductRepository catalog;
  private final StockLevelsGateway stock;

  public CartQuoteHandler(CatalogProductRepository catalog, StockLevelsGateway stock) {
    this.catalog = catalog;
    this.stock = stock;
  }

  @HandleQuery
  public CartQuote quote(QuoteCartQuery query) {
    List<UUID> ids = query.getItems().stream().map(Item::productId).distinct().toList();
    Map<UUID, CatalogProduct> known =
        ids.isEmpty()
            ? Map.of()
            : catalog.query().where("productId", Operators.IN, ids).findAll().stream()
                .collect(Collectors.toMap(CatalogProduct::getProductId, Function.identity()));
    List<UUID> forSale =
        ids.stream().filter(id -> known.containsKey(id) && known.get(id).isOrderable()).toList();
    Optional<Map<UUID, Integer>> levels =
        forSale.isEmpty() ? Optional.of(Map.of()) : stock.available(forSale);
    Map<UUID, Integer> wanted =
        query.getItems().stream()
            .collect(Collectors.groupingBy(Item::productId, Collectors.summingInt(Item::quantity)));

    List<QuoteLine> lines =
        query.getItems().stream()
            .map(item -> line(item, known.get(item.productId()), levels, wanted))
            .toList();
    List<QuoteLine> priced = lines.stream().filter(l -> l.unitPrice() != null).toList();
    BigDecimal total =
        priced.stream().map(QuoteLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    String currency =
        priced.isEmpty() ? null : known.get(priced.getFirst().productId()).getCurrency();
    boolean orderable = !lines.isEmpty() && lines.stream().allMatch(l -> l.problem() == null);
    return new CartQuote(lines, total, currency, levels.isPresent(), orderable);
  }

  private static QuoteLine line(
      Item item,
      CatalogProduct product,
      Optional<Map<UUID, Integer>> levels,
      Map<UUID, Integer> wanted) {
    String sku = product == null ? null : product.getSku();
    String name = product == null ? null : product.getName();
    if (product == null || !product.isOrderable()) {
      return new QuoteLine(
          item.productId(),
          sku,
          name,
          item.quantity(),
          null,
          null,
          null,
          QuoteProblem.NOT_FOR_SALE);
    }
    Integer available = levels.map(l -> l.getOrDefault(item.productId(), 0)).orElse(null);
    boolean shortOfStock = available != null && wanted.get(item.productId()) > available;
    return new QuoteLine(
        item.productId(),
        sku,
        name,
        item.quantity(),
        product.getPrice(),
        product.getPrice().multiply(BigDecimal.valueOf(item.quantity())),
        available,
        shortOfStock ? QuoteProblem.NOT_ENOUGH_STOCK : null);
  }
}
