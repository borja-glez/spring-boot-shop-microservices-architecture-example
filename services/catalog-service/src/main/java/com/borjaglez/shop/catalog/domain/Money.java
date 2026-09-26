package com.borjaglez.shop.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import com.borjaglez.shop.support.error.BusinessRuleViolationException;

/**
 * A positive amount of money with at most two decimals, stored with exactly two.
 *
 * <p>Mapped as an embeddable record so queries can reach {@code price.amount} and {@code
 * price.currency} through specification-repository paths.
 */
@Embeddable
public record Money(
    @Column(name = "price_amount", nullable = false, precision = 12, scale = 2) BigDecimal amount,
    @Column(name = "price_currency", nullable = false, length = 3) String currency) {

  public Money {
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
    if (amount.signum() <= 0) {
      throw new BusinessRuleViolationException("invalid-price", "Price must be greater than zero");
    }
    if (amount.stripTrailingZeros().scale() > 2) {
      throw new BusinessRuleViolationException(
          "invalid-price", "Price must have at most two decimals");
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
      throw new BusinessRuleViolationException(
          "invalid-currency", "Unknown ISO 4217 currency: " + code);
    }
  }
}
