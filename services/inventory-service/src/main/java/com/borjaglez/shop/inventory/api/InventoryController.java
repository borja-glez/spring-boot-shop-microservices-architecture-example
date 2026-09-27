package com.borjaglez.shop.inventory.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.inventory.api.dto.AdjustStockRequest;
import com.borjaglez.shop.inventory.application.command.AdjustStockCommand;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.ReservationView;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.SearchReservationsQuery;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.SearchStockQuery;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.StockView;
import com.borjaglez.shop.inventory.domain.Reservation;
import com.borjaglez.shop.inventory.domain.StockItem;
import com.borjaglez.shop.support.web.CurrentUser;
import com.borjaglez.shop.support.web.PageResponse;
import com.borjaglez.specrepository.core.QueryPlan;
import com.borjaglez.specrepository.http.spring.FilterableQuery;

/**
 * Inventory backoffice. There is no real authentication in the example, so any user can count
 * stock; the user id is recorded in the {@code StockAdjusted} event.
 */
@RestController
@RequestMapping("/api/inventory")
class InventoryController {

  private final CommandBus commands;
  private final QueryBus queries;

  InventoryController(CommandBus commands, QueryBus queries) {
    this.commands = commands;
    this.queries = queries;
  }

  @GetMapping("/stock")
  PageResponse<StockView> stock(
      @FilterableQuery(
              value = StockItem.class,
              filterableFields = {"sku", "name", "onHand", "reserved", "updatedAt"},
              sortableFields = {"sku", "name", "onHand", "reserved", "updatedAt"})
          QueryPlan<StockItem> plan,
      Pageable pageable) {
    Page<StockView> page = queries.ask(new SearchStockQuery(plan, pageable));
    return PageResponse.of(page);
  }

  @PutMapping("/stock/{productId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void adjust(
      @CurrentUser String user,
      @PathVariable UUID productId,
      @Valid @RequestBody AdjustStockRequest body) {
    commands.dispatchAndWait(new AdjustStockCommand(productId, body.onHand(), user));
  }

  @GetMapping("/reservations")
  PageResponse<ReservationView> reservations(
      @FilterableQuery(
              value = Reservation.class,
              filterableFields = {"orderId", "status", "reservedAt", "releasedAt", "lines.sku"},
              sortableFields = {"reservedAt", "releasedAt"})
          QueryPlan<Reservation> plan,
      Pageable pageable) {
    Page<ReservationView> page = queries.ask(new SearchReservationsQuery(plan, pageable));
    return PageResponse.of(page);
  }
}
