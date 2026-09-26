package com.borjaglez.shop.inventory.application.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.ReservationView;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.SearchReservationsQuery;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.SearchStockQuery;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.StockView;
import com.borjaglez.shop.inventory.domain.ReservationRepository;
import com.borjaglez.shop.inventory.domain.StockItemRepository;
import com.borjaglez.shop.support.query.QueryPlans;

/** Read side of the inventory backoffice. Every read uses specification-repository. */
@QueryHandler
public class InventoryQueryHandler {

  private static final Sort BY_SKU = Sort.by("sku");
  private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "reservedAt");

  private final StockItemRepository stock;
  private final ReservationRepository reservations;

  public InventoryQueryHandler(StockItemRepository stock, ReservationRepository reservations) {
    this.stock = stock;
    this.reservations = reservations;
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public Page<StockView> stock(SearchStockQuery query) {
    return stock
        .findAll(QueryPlans.sortedByDefault(query.getPlan(), BY_SKU), query.getPageable())
        .map(StockView::of);
  }

  @HandleQuery
  @Transactional(readOnly = true)
  public Page<ReservationView> reservations(SearchReservationsQuery query) {
    return reservations
        .findAll(QueryPlans.sortedByDefault(query.getPlan(), NEWEST_FIRST), query.getPageable())
        .map(ReservationView::of);
  }
}
