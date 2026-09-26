package com.borjaglez.shop.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.test.util.ReflectionTestUtils;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.shop.contracts.orders.OrderLine;
import com.borjaglez.shop.contracts.orders.OrderPlaced;

/** Builders for the order events reporting consumes. */
public final class ReportingTestEvents {

  private ReportingTestEvents() {}

  /** An order of {@code units} of one product at {@code price}, placed on {@code day}. */
  public static OrderPlaced placed(
      UUID orderId, String customer, String sku, int units, String price, String day) {
    BigDecimal unitPrice = new BigDecimal(price);
    OrderPlaced event =
        new OrderPlaced(
            orderId,
            customer,
            List.of(
                new OrderLine(
                    UUID.nameUUIDFromBytes(sku.getBytes()),
                    sku,
                    "Producto " + sku,
                    units,
                    unitPrice)),
            unitPrice.multiply(BigDecimal.valueOf(units)),
            "EUR");
    return at(event, Instant.parse(day + "T10:00:00Z"));
  }

  public static <E extends Event> E at(E event, Instant occurredOn) {
    ReflectionTestUtils.setField(event, Event.class, "occurredOn", occurredOn, Instant.class);
    return event;
  }
}
