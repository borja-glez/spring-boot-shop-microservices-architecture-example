package com.borjaglez.shop.inventory.application.command;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.shop.contracts.inventory.ReleaseStock;
import com.borjaglez.shop.contracts.inventory.ReservationLine;
import com.borjaglez.shop.contracts.inventory.ReserveStock;
import com.borjaglez.shop.contracts.inventory.StockAdjusted;
import com.borjaglez.shop.contracts.inventory.StockRelease;
import com.borjaglez.shop.contracts.inventory.StockReleased;
import com.borjaglez.shop.contracts.inventory.StockReservation;
import com.borjaglez.shop.contracts.inventory.StockReserved;
import com.borjaglez.shop.contracts.inventory.StockShortage;
import com.borjaglez.shop.eskit.EventStore;
import com.borjaglez.shop.inventory.domain.Reservation;
import com.borjaglez.shop.inventory.domain.ReservationRepository;
import com.borjaglez.shop.inventory.domain.ReservedLine;
import com.borjaglez.shop.inventory.domain.StockItem;
import com.borjaglez.shop.inventory.domain.StockItemRepository;
import com.borjaglez.shop.support.error.NotFoundException;
import com.borjaglez.specrepository.core.Operators;

/**
 * Write side of the inventory. {@link ReserveStock} and {@link ReleaseStock} arrive from the
 * checkout saga over RabbitMQ and answer with a result: a shortage is an answer, not an error. Both
 * are idempotent per order, because the saga retries them after a timeout without knowing whether
 * the first attempt ran.
 */
@CommandHandler
public class InventoryCommandHandler {

  static final String RESERVATION_STREAM = "reservation";
  static final String STOCK_STREAM = "stock";

  private final StockItemRepository stock;
  private final ReservationRepository reservations;
  private final EventStore eventStore;
  private final Clock clock;
  private final StockTransactions transactions;

  public InventoryCommandHandler(
      StockItemRepository stock,
      ReservationRepository reservations,
      EventStore eventStore,
      Clock clock,
      StockTransactions transactions) {
    this.stock = stock;
    this.reservations = reservations;
    this.eventStore = eventStore;
    this.clock = clock;
    this.transactions = transactions;
  }

  @HandleCommand
  public StockReservation reserve(ReserveStock command) {
    return transactions.run(() -> reserveOnce(command));
  }

  private StockReservation reserveOnce(ReserveStock command) {
    if (command.getOrderId() == null
        || command.getLines() == null
        || command.getLines().isEmpty()) {
      throw new IllegalArgumentException("A reservation needs an order and at least one line");
    }
    Optional<Reservation> existing = reservation(command.getOrderId());
    if (existing.isPresent()) {
      // A retry of a reservation that went through, or one that arrives after the release.
      return new StockReservation(existing.get().isReserved(), List.of());
    }
    Map<UUID, ReservationLine> wanted = merged(command.getLines());
    Map<UUID, StockItem> items =
        stock
            .query()
            .where("productId", Operators.IN, List.copyOf(wanted.keySet()))
            .findAll()
            .stream()
            .collect(Collectors.toMap(StockItem::getProductId, Function.identity()));
    List<String> unknown =
        wanted.values().stream()
            .filter(line -> !items.containsKey(line.productId()))
            .map(ReservationLine::sku)
            .toList();
    if (!unknown.isEmpty()) {
      // Not a shortage: the catalog event has not reached the inventory yet. Failing makes the
      // saga retry a little later instead of rejecting an order that may well be served.
      throw new IllegalStateException("The inventory does not know " + unknown + " yet");
    }
    List<StockShortage> shortages = new ArrayList<>();
    for (ReservationLine line : wanted.values()) {
      int available = items.get(line.productId()).available();
      if (available < line.quantity()) {
        shortages.add(new StockShortage(line.productId(), line.sku(), line.quantity(), available));
      }
    }
    if (!shortages.isEmpty()) {
      return new StockReservation(false, shortages);
    }
    OffsetDateTime now = OffsetDateTime.now(clock);
    for (ReservationLine line : wanted.values()) {
      items.get(line.productId()).reserve(line.quantity(), now);
    }
    stock.saveAll(items.values());
    List<ReservedLine> lines =
        wanted.values().stream()
            .map(l -> new ReservedLine(l.productId(), l.sku(), l.quantity()))
            .toList();
    reservations.save(Reservation.reserved(command.getOrderId(), lines, now));
    eventStore.record(
        RESERVATION_STREAM,
        command.getOrderId().toString(),
        List.of(new StockReserved(command.getOrderId(), List.copyOf(wanted.values()))));
    return new StockReservation(true, List.of());
  }

  @HandleCommand
  public StockRelease release(ReleaseStock command) {
    return transactions.run(() -> releaseOnce(command));
  }

  private StockRelease releaseOnce(ReleaseStock command) {
    OffsetDateTime now = OffsetDateTime.now(clock);
    Optional<Reservation> existing = reservation(command.getOrderId());
    if (existing.isEmpty()) {
      // Nothing held yet: leave a tombstone so a late reserve command cannot hold stock.
      reservations.save(Reservation.releasedBeforeReserving(command.getOrderId(), now));
      return new StockRelease(false);
    }
    Reservation reservation = existing.get();
    if (!reservation.isReserved()) {
      return new StockRelease(false);
    }
    Map<UUID, StockItem> items =
        stock
            .query()
            .where(
                "productId",
                Operators.IN,
                reservation.getLines().stream().map(ReservedLine::getProductId).toList())
            .findAll()
            .stream()
            .collect(Collectors.toMap(StockItem::getProductId, Function.identity()));
    for (ReservedLine line : reservation.getLines()) {
      items.get(line.getProductId()).release(line.getQuantity(), now);
    }
    stock.saveAll(items.values());
    reservation.release(now);
    reservations.save(reservation);
    eventStore.record(
        RESERVATION_STREAM,
        command.getOrderId().toString(),
        List.of(
            new StockReleased(
                command.getOrderId(),
                reservation.getLines().stream()
                    .map(l -> new ReservationLine(l.getProductId(), l.getSku(), l.getQuantity()))
                    .toList())));
    return new StockRelease(true);
  }

  @HandleCommand
  @Transactional
  public void adjust(AdjustStockCommand command) {
    StockItem item =
        stock
            .query()
            .where("productId", Operators.EQUALS, command.getProductId())
            .findOne()
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "stock-item-not-found",
                        "No stock is kept for product " + command.getProductId()));
    int previous = item.adjust(command.getOnHand(), OffsetDateTime.now(clock));
    stock.save(item);
    eventStore.record(
        STOCK_STREAM,
        item.getProductId().toString(),
        List.of(
            new StockAdjusted(
                item.getProductId(),
                item.getSku(),
                item.getOnHand(),
                previous,
                command.getAdjustedBy())));
  }

  private Optional<Reservation> reservation(UUID orderId) {
    return reservations.query().where("orderId", Operators.EQUALS, orderId).findOne();
  }

  /** One line per product, adding up repeated ones. */
  private static Map<UUID, ReservationLine> merged(List<ReservationLine> lines) {
    Map<UUID, ReservationLine> merged = new LinkedHashMap<>();
    for (ReservationLine line : lines) {
      if (line.quantity() < 1) {
        throw new IllegalArgumentException("Quantities must be positive: " + line);
      }
      merged.merge(
          line.productId(),
          line,
          (a, b) -> new ReservationLine(a.productId(), a.sku(), a.quantity() + b.quantity()));
    }
    return merged;
  }
}
