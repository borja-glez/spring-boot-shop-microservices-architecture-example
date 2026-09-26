package com.borjaglez.shop.reporting.domain;

/** Status of an order as reporting sees it. It only moves forward (see {@link #isBefore}). */
public enum ReportStatus {
  PLACED(0),
  CONFIRMED(1),
  REJECTED(1),
  CANCELLED(2);

  private final int rank;

  ReportStatus(int rank) {
    this.rank = rank;
  }

  public boolean isBefore(ReportStatus next) {
    return rank < next.rank;
  }
}
