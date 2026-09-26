package com.borjaglez.shop.orders.checkout;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.borjaglez.shop.contracts.inventory.ReservationLine;
import com.borjaglez.shop.contracts.inventory.StockRelease;
import com.borjaglez.shop.contracts.inventory.StockReservation;
import com.borjaglez.shop.contracts.inventory.StockShortage;
import com.borjaglez.shop.contracts.payments.PaymentAuthorization;
import com.borjaglez.shop.contracts.payments.PaymentRefund;
import com.borjaglez.shop.orders.application.checkout.InventoryGateway;
import com.borjaglez.shop.orders.application.checkout.PaymentsGateway;

/**
 * Inventory and payments that answer as each test tells them, per order, so tests sharing a context
 * do not see each other's scripts. By default everything succeeds.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeCheckout {

  /** What the fake services do for one order. */
  public static class Script {
    volatile int inventoryFailures;
    volatile boolean inventoryErrors;
    volatile int paymentFailures;
    volatile int releaseFailures;
    volatile boolean shortage;
    volatile String declineReason;
    final List<String> calls = new CopyOnWriteArrayList<>();

    public Script inventoryFails(int times) {
      inventoryFailures = times;
      return this;
    }

    /**
     * The reservation throws an Error, as a native image does when a reflection hint is missing.
     */
    public Script inventoryThrowsErrors() {
      inventoryErrors = true;
      return this;
    }

    public Script paymentFails(int times) {
      paymentFailures = times;
      return this;
    }

    public Script releaseFails(int times) {
      releaseFailures = times;
      return this;
    }

    public Script outOfStock() {
      shortage = true;
      return this;
    }

    public Script declines(String reason) {
      declineReason = reason;
      return this;
    }

    public List<String> calls() {
      return List.copyOf(calls);
    }
  }

  private final Map<UUID, Script> scripts = new ConcurrentHashMap<>();

  /** The script of an order, created on first use. */
  public Script script(UUID orderId) {
    return scripts.computeIfAbsent(orderId, id -> new Script());
  }

  @Bean
  @Primary
  InventoryGateway fakeInventory() {
    return new InventoryGateway() {
      @Override
      public StockReservation reserve(UUID orderId, List<ReservationLine> lines) {
        Script script = script(orderId);
        script.calls.add("reserve");
        if (script.inventoryErrors) {
          throw new LinkageError("Record components not available for StockReservation");
        }
        if (script.inventoryFailures > 0) {
          script.inventoryFailures--;
          throw new IllegalStateException("inventory timed out");
        }
        if (script.shortage) {
          ReservationLine first = lines.getFirst();
          return new StockReservation(
              false,
              List.of(new StockShortage(first.productId(), first.sku(), first.quantity(), 0)));
        }
        return new StockReservation(true, List.of());
      }

      @Override
      public StockRelease release(UUID orderId) {
        Script script = script(orderId);
        script.calls.add("release");
        if (script.releaseFailures > 0) {
          script.releaseFailures--;
          throw new IllegalStateException("inventory down");
        }
        return new StockRelease(true);
      }
    };
  }

  @Bean
  @Primary
  PaymentsGateway fakePayments() {
    return new PaymentsGateway() {
      @Override
      public PaymentAuthorization authorize(
          UUID orderId, String customerId, BigDecimal amount, String currency) {
        Script script = script(orderId);
        script.calls.add("authorize");
        if (script.paymentFailures > 0) {
          script.paymentFailures--;
          throw new IllegalStateException("payments timed out");
        }
        return script.declineReason == null
            ? new PaymentAuthorization(true, UUID.randomUUID(), null)
            : new PaymentAuthorization(false, UUID.randomUUID(), script.declineReason);
      }

      @Override
      public PaymentRefund refund(UUID orderId) {
        script(orderId).calls.add("refund");
        return new PaymentRefund(true);
      }
    };
  }
}
