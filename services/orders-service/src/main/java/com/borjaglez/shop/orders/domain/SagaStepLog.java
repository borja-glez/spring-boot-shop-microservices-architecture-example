package com.borjaglez.shop.orders.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One attempt of a saga step, for the checkout timeline. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SagaStepLog {

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private CheckoutStep step;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private StepOutcome outcome;

  @Column(length = 500)
  private String detail;

  @Column(name = "at", nullable = false)
  private OffsetDateTime at;
}
