package com.borjaglez.shop.contracts.payments;

import java.math.BigDecimal;
import java.util.UUID;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Authorizes the payment of an order. Answered with a {@link PaymentAuthorization}. Idempotent:
 * asking again for the same order returns the first answer.
 */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "payments", module = "payment", name = "authorize-payment")
public class AuthorizePayment extends Command {

  private UUID orderId;
  private String customerId;
  private BigDecimal amount;
  private String currency;
}
