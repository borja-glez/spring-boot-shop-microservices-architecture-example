package com.borjaglez.shop.payments.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.borjaglez.cqrs.rabbitmq.RabbitMqCommandBus;
import com.borjaglez.cqrs.rabbitmq.RemoteHandlerException;
import com.borjaglez.shop.contracts.payments.AuthorizePayment;
import com.borjaglez.shop.contracts.payments.PaymentAuthorization;
import com.borjaglez.shop.contracts.payments.PaymentRefund;
import com.borjaglez.shop.contracts.payments.RefundPayment;
import com.borjaglez.shop.support.chaos.Chaos;
import com.borjaglez.shop.testsupport.KafkaTestConfiguration;
import com.borjaglez.shop.testsupport.PostgresTestConfiguration;
import com.borjaglez.shop.testsupport.RabbitTestConfiguration;

/** The payment commands as the checkout saga sends them: over RabbitMQ, waiting for the answer. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({
  PostgresTestConfiguration.class,
  KafkaTestConfiguration.class,
  RabbitTestConfiguration.class
})
class PaymentsOverRabbitIT {

  @Autowired RabbitMqCommandBus rabbit;
  @Autowired Chaos chaos;

  @AfterEach
  void calmDown() {
    chaos.reset();
  }

  @Test
  void theSagaAuthorizesAndRefundsOverRabbitMq() {
    UUID orderId = UUID.randomUUID();

    PaymentAuthorization authorization =
        rabbit.dispatchAndReceive(
            new AuthorizePayment(orderId, "cliente-lucia", new BigDecimal("80.00"), "EUR"));
    PaymentRefund refund = rabbit.dispatchAndReceive(new RefundPayment(orderId));

    assertThat(authorization.authorized()).isTrue();
    assertThat(authorization.paymentId()).isNotNull();
    assertThat(refund.refunded()).isTrue();
  }

  @Test
  void aDeclinedCardIsAnAnswer() {
    PaymentAuthorization authorization =
        rabbit.dispatchAndReceive(
            new AuthorizePayment(
                UUID.randomUUID(), "cliente-lucia", new BigDecimal("1000"), "EUR"));

    assertThat(authorization.authorized()).isFalse();
    assertThat(authorization.reason()).isEqualTo("card-limit-exceeded");
  }

  @Test
  void anInvalidAmountFailsOnTheCallerSide() {
    assertThatThrownBy(
            () ->
                rabbit.dispatchAndReceive(
                    new AuthorizePayment(
                        UUID.randomUUID(), "cliente-lucia", BigDecimal.ZERO, "EUR")))
        .isInstanceOf(RemoteHandlerException.class)
        .hasMessageContaining("positive amount");
  }

  @Test
  void theDeclineAllFaultDeclinesEvenSmallPayments() {
    chaos.set("payments.decline-all", 1);

    PaymentAuthorization authorization =
        rabbit.dispatchAndReceive(
            new AuthorizePayment(UUID.randomUUID(), "cliente-lucia", BigDecimal.ONE, "EUR"));

    assertThat(authorization.authorized()).isFalse();
  }

  @Test
  void anInjectedFailureReachesTheSagaAsARemoteErrorAndNothingIsCharged() {
    UUID orderId = UUID.randomUUID();
    chaos.set("AuthorizePayment.fail", 1);

    assertThatThrownBy(
            () ->
                rabbit.dispatchAndReceive(
                    new AuthorizePayment(orderId, "cliente-lucia", BigDecimal.TEN, "EUR")))
        .isInstanceOf(RemoteHandlerException.class)
        .hasMessageContaining("failed on purpose");

    chaos.reset();
    PaymentAuthorization retried =
        rabbit.dispatchAndReceive(
            new AuthorizePayment(orderId, "cliente-lucia", BigDecimal.TEN, "EUR"));
    assertThat(retried.authorized()).isTrue();
  }
}
