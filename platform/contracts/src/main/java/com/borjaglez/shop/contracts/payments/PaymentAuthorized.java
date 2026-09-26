package com.borjaglez.shop.contracts.payments;

import java.math.BigDecimal;
import java.util.UUID;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** The payment of an order was authorized. */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "payments", module = "payment", name = "payment-authorized")
public class PaymentAuthorized extends Event {

  private UUID paymentId;
  private UUID orderId;
  private String customerId;
  private BigDecimal amount;
  private String currency;
}
