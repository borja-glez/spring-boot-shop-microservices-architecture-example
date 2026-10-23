package com.borjaglez.shop.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.borjaglez.shop.kernel.error.BusinessRuleViolationException;

class MoneyEmbeddableTest {

  @Test
  void keepsTwoDecimalsAndUppercaseCurrency() {
    MoneyEmbeddable money = MoneyEmbeddable.of("18.9", "eur");

    assertThat(money.amount()).isEqualByComparingTo("18.90");
    assertThat(money.amount().scale()).isEqualTo(2);
    assertThat(money.currency()).isEqualTo("EUR");
  }

  @Test
  void rejectsZeroAndNegativeAmounts() {
    assertThatThrownBy(() -> MoneyEmbeddable.of("0", "EUR"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "invalid-price");
    assertThatThrownBy(() -> MoneyEmbeddable.of("-1.50", "EUR"))
        .isInstanceOf(BusinessRuleViolationException.class);
  }

  @Test
  void rejectsMoreThanTwoDecimals() {
    assertThatThrownBy(() -> MoneyEmbeddable.of("1.999", "EUR"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "invalid-price");
  }

  @Test
  void rejectsUnknownCurrencyCodes() {
    assertThatThrownBy(() -> MoneyEmbeddable.of("10", "EURO"))
        .isInstanceOf(BusinessRuleViolationException.class)
        .hasFieldOrPropertyWithValue("code", "invalid-currency");
  }

  @Test
  void equalityIgnoresTrailingZeros() {
    assertThat(new MoneyEmbeddable(new BigDecimal("5.0"), "EUR"))
        .isEqualTo(MoneyEmbeddable.of("5.00", "EUR"));
  }
}
