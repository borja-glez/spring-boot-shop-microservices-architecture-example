package com.borjaglez.shop.contracts.notifications;

import java.util.UUID;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * The notices a customer received about one order, oldest first. Answered with {@link
 * OrderNotices}. It travels from the Boot 4 orders service to the Boot 3 notifications service, so
 * it is written by Jackson 3 and read by Jackson 2, and the answer the other way round.
 */
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@CqrsMessage(service = "notifications", module = "notices", name = "get-order-notices")
public class GetOrderNotices extends Query {

  private UUID orderId;
  private String customerId;
}
