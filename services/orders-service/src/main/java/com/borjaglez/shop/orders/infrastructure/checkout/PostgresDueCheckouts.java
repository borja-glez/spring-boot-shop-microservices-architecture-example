package com.borjaglez.shop.orders.infrastructure.checkout;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.borjaglez.shop.orders.application.checkout.DueCheckouts;

/**
 * Claims due sagas with one statement: {@code FOR UPDATE SKIP LOCKED} lets concurrent runners take
 * disjoint sets without waiting for each other, and the update that sets the lease commits at once.
 * Row locks cannot be expressed with specification-repository, so this is a native statement.
 */
@Component
class PostgresDueCheckouts implements DueCheckouts {

  static final String CLAIM =
      """
      update checkout_saga set next_attempt_at = :leasedUntil
      where order_id in (
          select order_id from checkout_saga
          where state in ('RUNNING', 'STUCK') and next_attempt_at <= :now
          order by next_attempt_at
          limit :max
          for update skip locked)
      returning order_id
      """;

  private final JdbcClient jdbc;

  PostgresDueCheckouts(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<UUID> claim(int max, OffsetDateTime now, OffsetDateTime leasedUntil) {
    return jdbc.sql(CLAIM)
        .param("leasedUntil", leasedUntil)
        .param("now", now)
        .param("max", max)
        .query(UUID.class)
        .list();
  }
}
