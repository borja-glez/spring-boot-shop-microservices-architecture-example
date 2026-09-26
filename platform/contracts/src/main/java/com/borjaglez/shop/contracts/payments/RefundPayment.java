package com.borjaglez.shop.contracts.payments;

import java.util.UUID;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** Refunds the payment of an order. Answered with a {@link PaymentRefund}. Idempotent. */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "payments", module = "payment", name = "refund-payment")
public class RefundPayment extends Command {

  private UUID orderId;
}
