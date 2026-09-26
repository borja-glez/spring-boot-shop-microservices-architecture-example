package com.borjaglez.shop.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.test.util.ReflectionTestUtils;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.shop.contracts.catalog.ProductDiscontinued;
import com.borjaglez.shop.contracts.catalog.ProductPriceChanged;
import com.borjaglez.shop.contracts.catalog.ProductPublished;

/** Builders for the catalog events the orders service consumes. */
public final class OrdersTestSupport {

  private OrdersTestSupport() {}

  public static ProductPublished published(UUID productId, String sku, String price) {
    return new ProductPublished(
        productId, sku, "Producto " + sku, new BigDecimal(price), "EUR", "seller-ana");
  }

  public static ProductPriceChanged priceChanged(UUID productId, String from, String to) {
    return new ProductPriceChanged(productId, new BigDecimal(from), new BigDecimal(to), "EUR");
  }

  public static ProductDiscontinued discontinued(UUID productId) {
    return new ProductDiscontinued(productId, "Fin de temporada");
  }

  /** Events are stamped when created; tests that reorder them set the time explicitly. */
  public static <E extends Event> E at(E event, Instant occurredOn) {
    ReflectionTestUtils.setField(event, Event.class, "occurredOn", occurredOn, Instant.class);
    return event;
  }
}
