package com.borjaglez.shop.payments.application.command;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.shop.contracts.payments.AuthorizePayment;
import com.borjaglez.shop.contracts.payments.PaymentAuthorization;
import com.borjaglez.shop.contracts.payments.PaymentRefund;
import com.borjaglez.shop.contracts.payments.RefundPayment;
import com.borjaglez.shop.eskit.AggregateStore;
import com.borjaglez.shop.payments.domain.Payment;
import com.borjaglez.shop.payments.domain.PaymentStatus;
import com.borjaglez.shop.payments.domain.PaymentView;
import com.borjaglez.shop.payments.domain.PaymentViewRepository;
import com.borjaglez.shop.support.chaos.Chaos;
import com.borjaglez.shop.support.chaos.ChaosFault;
import com.borjaglez.specrepository.core.Operators;

/**
 * Answers the payment commands of the checkout saga, which arrive over RabbitMQ. A declined card is
 * an answer, not an error. Both commands are idempotent per order: the saga retries them after a
 * timeout without knowing whether the first attempt ran.
 */
@CommandHandler
public class PaymentCommandHandler {

  private final AggregateStore<Payment> payments;
  private final PaymentViewRepository views;
  private final PaymentsProperties properties;
  private final Clock clock;
  private final ChaosFault declineAll;

  public PaymentCommandHandler(
      AggregateStore<Payment> payments,
      PaymentViewRepository views,
      PaymentsProperties properties,
      Clock clock,
      Chaos chaos) {
    this.payments = payments;
    this.views = views;
    this.properties = properties;
    this.clock = clock;
    this.declineAll =
        chaos.toggle(
            "payments.decline-all", "Lowers the card limit to zero: every new payment is declined");
  }

  @HandleCommand
  @Transactional
  public PaymentAuthorization authorize(AuthorizePayment command) {
    Optional<Payment> existing = payments.load(command.getOrderId().toString());
    if (existing.isPresent()) {
      return answer(existing.get());
    }
    Payment payment =
        Payment.authorize(
            command.getOrderId(),
            command.getCustomerId(),
            command.getAmount(),
            command.getCurrency(),
            declineAll.active() ? BigDecimal.ZERO : properties.cardLimit());
    payments.save(payment);
    views.save(created(payment));
    return answer(payment);
  }

  @HandleCommand
  @Transactional
  public PaymentRefund refund(RefundPayment command) {
    Optional<Payment> existing = payments.load(command.getOrderId().toString());
    if (existing.isEmpty()) {
      Payment voided = Payment.voided(command.getOrderId());
      payments.save(voided);
      views.save(created(voided));
      return new PaymentRefund(false);
    }
    Payment payment = existing.get();
    if (!payment.refund()) {
      return new PaymentRefund(false);
    }
    payments.save(payment);
    PaymentView view =
        views
            .query()
            .where("orderId", Operators.EQUALS, command.getOrderId())
            .findOne()
            .orElseThrow(
                () -> new IllegalStateException("No read model for " + command.getOrderId()));
    view.changed(PaymentStatus.REFUNDED, OffsetDateTime.now(clock));
    views.save(view);
    return new PaymentRefund(true);
  }

  private PaymentView created(Payment payment) {
    return PaymentView.created(
        payment.orderId(),
        payment.paymentId(),
        payment.customerId(),
        payment.amount(),
        payment.currency(),
        payment.status(),
        payment.reason(),
        OffsetDateTime.now(clock));
  }

  private static PaymentAuthorization answer(Payment payment) {
    return new PaymentAuthorization(
        payment.wasAuthorized(),
        payment.paymentId(),
        payment.wasAuthorized() ? null : payment.reason());
  }
}
