package com.borjaglez.shop.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

import com.borjaglez.shop.catalog.domain.exception.InvalidCurrencyException;
import com.borjaglez.shop.catalog.domain.exception.InvalidPriceException;

/** [DDD · Value Object] A positive amount of money with at most two decimals, kept with two. */
public record Money(BigDecimal amount, String currency) {

  public Money {
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
    if (amount.signum() <= 0) {
      throw new InvalidPriceException("Price must be greater than zero");
    }
    if (amount.stripTrailingZeros().scale() > 2) {
      throw new InvalidPriceException("Price must have at most two decimals");
    }
    amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    currency = validCurrency(currency);
  }

  public static Money of(String amount, String currency) {
    return new Money(new BigDecimal(amount), currency);
  }

  public boolean sameCurrencyAs(Money other) {
    return currency.equals(other.currency);
  }

  private static String validCurrency(String code) {
    String normalised = code.trim().toUpperCase(Locale.ROOT);
    try {
      return Currency.getInstance(normalised).getCurrencyCode();
    } catch (IllegalArgumentException e) {
      throw new InvalidCurrencyException("Unknown ISO 4217 currency: " + code);
    }
  }
}
