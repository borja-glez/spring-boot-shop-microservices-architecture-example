package com.borjaglez.shop.inventory.application.query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.shop.inventory.domain.Reservation;
import com.borjaglez.shop.inventory.domain.ReservationStatus;
import com.borjaglez.shop.inventory.domain.StockItem;
import com.borjaglez.specrepository.core.QueryPlan;

import lombok.Getter;

/** Queries and views of the inventory backoffice. */
public final class InventoryQueries {

  private InventoryQueries() {}

  /** Stock matching the client plan. Answered with a {@code Page<StockView>}. */
  @Getter
  public static class SearchStockQuery extends Query {

    private final QueryPlan<StockItem> plan;
    private final Pageable pageable;

    public SearchStockQuery(QueryPlan<StockItem> plan, Pageable pageable) {
      this.plan = Objects.requireNonNull(plan, "plan must not be null");
      this.pageable = Objects.requireNonNull(pageable, "pageable must not be null");
    }
  }

  /** Reservations matching the client plan. Answered with a {@code Page<ReservationView>}. */
  @Getter
  public static class SearchReservationsQuery extends Query {

    private final QueryPlan<Reservation> plan;
    private final Pageable pageable;

    public SearchReservationsQuery(QueryPlan<Reservation> plan, Pageable pageable) {
      this.plan = Objects.requireNonNull(plan, "plan must not be null");
      this.pageable = Objects.requireNonNull(pageable, "pageable must not be null");
    }
  }

  public record StockView(
      UUID productId,
      String sku,
      String name,
      int onHand,
      int reserved,
      int available,
      OffsetDateTime updatedAt) {

    static StockView of(StockItem item) {
      return new StockView(
          item.getProductId(),
          item.getSku(),
          item.getName(),
          item.getOnHand(),
          item.getReserved(),
          item.available(),
          item.getUpdatedAt());
    }
  }

  public record ReservationView(
      UUID orderId,
      ReservationStatus status,
      OffsetDateTime reservedAt,
      OffsetDateTime releasedAt,
      List<LineView> lines) {

    static ReservationView of(Reservation reservation) {
      return new ReservationView(
          reservation.getOrderId(),
          reservation.getStatus(),
          reservation.getReservedAt(),
          reservation.getReleasedAt(),
          reservation.getLines().stream()
              .map(l -> new LineView(l.getProductId(), l.getSku(), l.getQuantity()))
              .toList());
    }
  }

  public record LineView(UUID productId, String sku, int quantity) {}
}
