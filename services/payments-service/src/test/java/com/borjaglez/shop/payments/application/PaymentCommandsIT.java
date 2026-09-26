package com.borjaglez.shop.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.contracts.payments.AuthorizePayment;
import com.borjaglez.shop.contracts.payments.PaymentAuthorization;
import com.borjaglez.shop.contracts.payments.PaymentRefund;
import com.borjaglez.shop.contracts.payments.RefundPayment;
import com.borjaglez.shop.payments.application.query.PaymentQueries.GetPaymentHistoryQuery;
import com.borjaglez.shop.payments.application.query.PaymentQueries.PaymentEvent;
import com.borjaglez.shop.payments.application.query.PaymentQueries.PaymentSummary;
import com.borjaglez.shop.payments.application.query.PaymentQueries.SearchPaymentsQuery;
import com.borjaglez.shop.payments.domain.PaymentStatus;
import com.borjaglez.shop.payments.domain.PaymentView;
import com.borjaglez.shop.support.error.NotFoundException;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;
import com.borjaglez.specrepository.core.Operators;
import com.borjaglez.specrepository.core.SpecificationQueryBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class
})
class PaymentCommandsIT {

  @Autowired CommandBus commands;
  @Autowired QueryBus queries;

  private PaymentAuthorization authorize(UUID orderId, String amount) {
    return commands.dispatchAndReceive(
        new AuthorizePayment(orderId, "cliente-lucia", new BigDecimal(amount), "EUR"));
  }

  private boolean refund(UUID orderId) {
    return commands.<PaymentRefund>dispatchAndReceive(new RefundPayment(orderId)).refunded();
  }

  private PaymentSummary view(UUID orderId) {
    List<PaymentSummary> found =
        queries
            .<Page<PaymentSummary>>ask(
                new SearchPaymentsQuery(
                    SpecificationQueryBuilder.forEntity(PaymentView.class)
                        .where("orderId", Operators.EQUALS, orderId)
                        .build(),
                    PageRequest.of(0, 5)))
            .getContent();
    assertThat(found).hasSize(1);
    return found.getFirst();
  }

  private List<String> history(UUID orderId) {
    return queries.<List<PaymentEvent>>ask(new GetPaymentHistoryQuery(orderId)).stream()
        .map(PaymentEvent::eventType)
        .toList();
  }

  @Test
  void aPaymentWithinTheLimitIsAuthorized() {
    UUID orderId = UUID.randomUUID();

    PaymentAuthorization result = authorize(orderId, "120.50");

    assertThat(result.authorized()).isTrue();
    assertThat(result.reason()).isNull();
    assertThat(view(orderId).status()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(history(orderId))
        .containsExactly("shop.payments.1.event.payment.payment-authorized");
  }

  @Test
  void aPaymentAboveTheLimitIsDeclined() {
    UUID orderId = UUID.randomUUID();

    PaymentAuthorization result = authorize(orderId, "450.00");

    assertThat(result.authorized()).isFalse();
    assertThat(result.reason()).isEqualTo("card-limit-exceeded");
    assertThat(view(orderId).reason()).isEqualTo("card-limit-exceeded");
  }

  @Test
  void authorizingTwiceChargesOnce() {
    UUID orderId = UUID.randomUUID();

    PaymentAuthorization first = authorize(orderId, "50");
    PaymentAuthorization retry = authorize(orderId, "50");

    assertThat(retry).isEqualTo(first);
    assertThat(history(orderId)).hasSize(1);
  }

  @Test
  void aRefundHappensOnce() {
    UUID orderId = UUID.randomUUID();
    authorize(orderId, "50");

    assertThat(refund(orderId)).isTrue();
    assertThat(refund(orderId)).isFalse();

    assertThat(view(orderId).status()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(history(orderId))
        .containsExactly(
            "shop.payments.1.event.payment.payment-authorized",
            "shop.payments.1.event.payment.payment-refunded");
  }

  @Test
  void anAuthorizationArrivingAfterTheRefundChargesNothing() {
    UUID orderId = UUID.randomUUID();

    assertThat(refund(orderId)).isFalse();
    PaymentAuthorization late = authorize(orderId, "50");

    assertThat(late.authorized()).isFalse();
    assertThat(late.reason()).isEqualTo("voided");
    assertThat(view(orderId).status()).isEqualTo(PaymentStatus.DECLINED);
  }

  @Test
  void anOrderWithoutPaymentHasNoHistory() {
    assertThatThrownBy(() -> history(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
  }
}
