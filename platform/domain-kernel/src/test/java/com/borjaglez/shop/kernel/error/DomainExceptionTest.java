package com.borjaglez.shop.kernel.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DomainExceptionTest {

  @Test
  void subtypesCarryCodeAndMessage() {
    DomainException[] errors = {
      new NotFoundException("product-not-found", "missing"),
      new ConflictException("sku-taken", "taken"),
      new BusinessRuleViolationException("out-of-stock", "empty")
    };

    assertThat(errors)
        .extracting(DomainException::code)
        .containsExactly("product-not-found", "sku-taken", "out-of-stock");
    assertThat(errors)
        .extracting(Throwable::getMessage)
        .containsExactly("missing", "taken", "empty");
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "abc-123", "a-b-c", "0"})
  void acceptsKebabCaseCodes(String code) {
    assertThat(new NotFoundException(code, "m").code()).isEqualTo(code);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Upper", "snake_case", "-lead", "trail-", "double--dash", "sp ace"})
  void rejectsOtherCodes(String code) {
    assertThatThrownBy(() -> new NotFoundException(code, "m"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(code);
  }

  @Test
  void rejectsNullCode() {
    assertThatThrownBy(() -> new ConflictException(null, "m"))
        .isInstanceOf(NullPointerException.class);
  }
}
