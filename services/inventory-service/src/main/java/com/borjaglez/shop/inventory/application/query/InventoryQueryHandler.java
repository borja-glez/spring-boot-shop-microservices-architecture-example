package com.borjaglez.shop.inventory.application.query;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.contracts.inventory.GetStockLevels;
import com.borjaglez.shop.contracts.inventory.StockLevel;
import com.borjaglez.shop.contracts.inventory.StockLevels;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.ReservationView;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.SearchReservationsQuery;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.SearchStockQuery;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.StockView;
import com.borjaglez.shop.inventory.domain.ReservationRepository;
import com.borjaglez.shop.inventory.domain.StockItemRepository;
import com.borjaglez.specrepository.core.Operators;

/**
 * Read side of the inventory: the backoffice queries, local, and {@link GetStockLevels}, which the
 * catalog and orders services ask over RabbitMQ. Every read uses specification-repository.
 */
@QueryHandler
public class InventoryQueryHandler {

  private static final Sort BY_SKU = Sort.by("sku");

  /** A product page asks for one product and a cart for at most its 20 lines. */
  static final int MAX_STOCK_LEVELS = 100;

  /**
   * Newest first, then by order: a server sort, so it may break ties with {@code orderId}, which
   * the client can filter by but not sort by.
   */
  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("reservedAt"), Sort.Order.asc("orderId"));

  /**
   * The lists take the client's filters: a combination the indexes do not cover must not hold a
   * connection for long. Spring applies the transaction timeout to every JPA query run in it.
   */
  static final int LIST_TIMEOUT_SECONDS = 5;

  private final StockItemRepository stock;
  private final ReservationRepository reservations;

  public InventoryQueryHandler(StockItemRepository stock, ReservationRepository reservations) {
    this.stock = stock;
    this.reservations = reservations;
  }

  @HandleQuery
  @Transactional(readOnly = true, timeout = LIST_TIMEOUT_SECONDS)
  public Page<StockView> stock(SearchStockQuery query) {
    return stock
        .query(query.getPlan())
        .sortedByDefault(BY_SKU)
        .findAll(query.getPageable())
        .map(StockView::of);
  }

  @HandleQuery
  @Transactional(readOnly = true, timeout = LIST_TIMEOUT_SECONDS)
  public Page<ReservationView> reservations(SearchReservationsQuery query) {
    return reservations
        .query(query.getPlan())
        .sortedByDefault(NEWEST_FIRST)
        .findAll(query.getPageable())
        .map(ReservationView::of);
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public StockLevels levels(GetStockLevels query) {
    List<UUID> ids = query.getProductIds() == null ? List.of() : query.getProductIds();
    if (ids.size() > MAX_STOCK_LEVELS) {
      throw new IllegalArgumentException(
          "At most " + MAX_STOCK_LEVELS + " products per request, got " + ids.size());
    }
    if (ids.isEmpty()) {
      return new StockLevels(List.of());
    }
    return new StockLevels(
        stock.query().where("productId", Operators.IN, ids).findAll().stream()
            .map(item -> new StockLevel(item.getProductId(), item.available()))
            .toList());
  }
}
