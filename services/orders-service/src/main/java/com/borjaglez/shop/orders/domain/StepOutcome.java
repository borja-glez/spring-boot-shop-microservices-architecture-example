package com.borjaglez.shop.orders.domain;

/** What happened to one attempt of a saga step. */
public enum StepOutcome {
  /** The step did its job. */
  SUCCEEDED,
  /** The other service answered no: no stock, card declined. */
  DECLINED,
  /** A technical failure; the step runs again later. */
  RETRYING,
  /** Too many technical failures; the saga compensates or needs an operator. */
  GAVE_UP
}
