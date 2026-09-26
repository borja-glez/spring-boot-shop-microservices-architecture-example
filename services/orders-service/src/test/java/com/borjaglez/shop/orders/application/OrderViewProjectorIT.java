package com.borjaglez.shop.orders.application;

import static com.borjaglez.shop.orders.OrdersTestSupport.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.shop.contracts.orders.OrderCancelled;
import com.borjaglez.shop.contracts.orders.OrderConfirmed;
import com.borjaglez.shop.contracts.orders.OrderLine;
import com.borjaglez.shop.contracts.orders.OrderPlaced;
import com.borjaglez.shop.contracts.orders.OrderRejected;
import com.borjaglez.shop.orders.application.projection.OrderViewProjector;
import com.borjaglez.shop.orders.domain.OrderStatus;
import com.borjaglez.shop.orders.domain.OrderView;
import com.borjaglez.shop.orders.domain.OrderViewLine;
import com.borjaglez.shop.orders.domain.OrderViewRepository;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;
import com.borjaglez.specrepository.core.Operators;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "shop.checkout.enabled=false")
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class
})
class OrderViewProjectorIT {

  private static final Instant T0 = Instant.parse("2026-09-25T10:00:00Z");

  @Autowired OrderViewProjector projector;
  @Autowired OrderViewRepository views;

  private static OrderPlaced placed(UUID orderId) {
    return new OrderPlaced(
        orderId,
        "cliente-lucia",
        List.of(
            new OrderLine(UUID.randomUUID(), "CAF-001", "Café", 2, new BigDecimal("5.00")),
            new OrderLine(UUID.randomUUID(), "ACE-001", "Aceite", 1, new BigDecimal("9.50"))),
        new BigDecimal("19.50"),
        "EUR");
  }

  private OrderView view(UUID orderId) {
    return views
        .query()
        .where("orderId", Operators.EQUALS, orderId)
        .leftFetch("lines")
        .findOne()
        .orElseThrow();
  }

  @Test
  void aPlacedOrderAppearsWithItsLines() {
    UUID orderId = UUID.randomUUID();

    projector.on(at(placed(orderId), T0));

    OrderView view = view(orderId);
    assertThat(view.getStatus()).isEqualTo(OrderStatus.PLACED);
    assertThat(view.getCustomerId()).isEqualTo("cliente-lucia");
    assertThat(view.getTotal()).isEqualByComparingTo("19.50");
    assertThat(view.getLineCount()).isEqualTo(2);
    assertThat(view.getLines())
        .extracting(OrderViewLine::getSku)
        .containsExactly("CAF-001", "ACE-001");
    assertThat(view.getPlacedAt()).isEqualTo(T0.atOffset(ZoneOffset.UTC));
  }

  @Test
  void aRedeliveredEventIsAppliedOnce() {
    UUID orderId = UUID.randomUUID();
    OrderPlaced event = at(placed(orderId), T0);

    projector.on(event);
    projector.on(event);

    assertThat(view(orderId).getLines()).hasSize(2);
  }

  @Test
  void aCancellationUpdatesTheStatus() {
    UUID orderId = UUID.randomUUID();

    projector.on(at(placed(orderId), T0));
    projector.on(
        at(new OrderCancelled(orderId, "Me equivoqué", "cliente-lucia"), T0.plusSeconds(60)));

    OrderView view = view(orderId);
    assertThat(view.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(view.getCancelReason()).isEqualTo("Me equivoqué");
    assertThat(view.getUpdatedAt()).isEqualTo(T0.plusSeconds(60).atOffset(ZoneOffset.UTC));
  }

  @Test
  void aCancellationArrivingFirstIsNotUndoneByTheLatePlacement() {
    UUID orderId = UUID.randomUUID();

    projector.on(
        at(new OrderCancelled(orderId, "Me equivoqué", "cliente-lucia"), T0.plusSeconds(60)));
    assertThat(view(orderId).getCustomerId()).isNull();

    projector.on(at(placed(orderId), T0));

    OrderView view = view(orderId);
    assertThat(view.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(view.getCustomerId()).isEqualTo("cliente-lucia");
    assertThat(view.getLines()).hasSize(2);
    assertThat(view.getUpdatedAt()).isEqualTo(T0.plusSeconds(60).atOffset(ZoneOffset.UTC));
  }

  @Test
  void aConfirmationArrivingFirstIsKeptWhenThePlacementArrives() {
    UUID orderId = UUID.randomUUID();
    UUID paymentId = UUID.randomUUID();

    projector.on(at(new OrderConfirmed(orderId, paymentId), T0.plusSeconds(5)));
    projector.on(at(placed(orderId), T0));

    OrderView view = view(orderId);
    assertThat(view.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(view.getPaymentId()).isEqualTo(paymentId);
    assertThat(view.getCustomerId()).isEqualTo("cliente-lucia");
  }

  @Test
  void aRejectionKeepsItsReasonAndIsNotUndone() {
    UUID orderId = UUID.randomUUID();

    projector.on(at(placed(orderId), T0));
    projector.on(
        at(new OrderRejected(orderId, "card-limit-exceeded", "Declined"), T0.plusSeconds(5)));
    projector.on(at(new OrderConfirmed(orderId, UUID.randomUUID()), T0.plusSeconds(1)));

    OrderView view = view(orderId);
    assertThat(view.getStatus()).isEqualTo(OrderStatus.REJECTED);
    assertThat(view.getRejectionReason()).isEqualTo("card-limit-exceeded");
    assertThat(view.getRejectionDetail()).isEqualTo("Declined");
  }
}
