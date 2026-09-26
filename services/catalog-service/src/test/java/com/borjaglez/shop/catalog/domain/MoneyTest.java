package com.borjaglez.shop.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.support.error.BusinessRuleViolationException;

class MoneyTest {

  @Test
  void keepsTwoDecimalsAndUppercaseCurrency() {
    Money money = Money.of("18.9", "eur");

    assertThat(money.amount()).isEqualByComparingTo("18.90");
    assertThat(money.amount().scale()).isEqualTo(2);
    assertThat(money.currency()).isEqualTo("EUR");
  }

  @Test
  void rejectsZeroAndNegativeAmounts() {
    assertThatThrownBy(() -> Money.of("0", "EUR"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "invalid-price");
    assertThatThrownBy(() -> Money.of("-1.50", "EUR"))
        .isInstanceOf(BusinessRuleViolationException.class);
  }

  @Test
  void rejectsMoreThanTwoDecimals() {
    assertThatThrownBy(() -> Money.of("1.999", "EUR"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "invalid-price");
  }

  @Test
  void rejectsUnknownCurrencyCodes() {
    assertThatThrownBy(() -> Money.of("10", "EURO"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "invalid-currency");
  }

  @Test
  void equalityIgnoresTrailingZeros() {
    assertThat(new Money(new BigDecimal("5.0"), "EUR")).isEqualTo(Money.of("5.00", "EUR"));
  }
}
