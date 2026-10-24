package com.borjaglez.shop.catalog.infrastructure.in.rest.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.ChangePriceRequestDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.CreateProductRequestDto;
import com.borjaglez.shop.catalog.infrastructure.in.rest.v1.model.DiscontinueRequestDto;

/**
 * The constraints of the contract, as the generated request models check them, accept and reject
 * exactly what the Bean Validation annotations of the catalog's requests define: {@code @NotBlank}
 * texts, and {@code @Positive @Digits(integer = 10, fraction = 2)} prices. The contract writes "not
 * blank" as a pattern, and a price as above 0 and at most 9999999999.99; the generated models add
 * the {@code @Digits} its {@code x-field-extra-annotation} names.
 */
class RequestConstraintsTest {

  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  /** The reference annotations. */
  record Reference(
      @NotBlank String text,
      @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal price) {}

  @AfterAll
  static void close() {
    FACTORY.close();
  }

  @Test
  void aTextIsBlankForTheContractExactlyWhenItIsBlankForBeanValidation() {
    List<String> texts = new ArrayList<>();
    IntStream.rangeClosed(Character.MIN_VALUE, Character.MAX_VALUE)
        .forEach(c -> texts.add(String.valueOf((char) c)));
    texts.addAll(
        List.of(
            "", " a ", "\t\n", "\u2003\u3000", "a\u2003", new String(Character.toChars(0x1F600))));

    for (String text : texts) {
      boolean notBlank = accepts(Reference.class, "text", text);
      assertThat(accepts(CreateProductRequestDto.class, "sku", text))
          .as("sku %s", codePoints(text))
          .isEqualTo(notBlank);
      assertThat(accepts(CreateProductRequestDto.class, "name", text))
          .as("name %s", codePoints(text))
          .isEqualTo(notBlank);
      assertThat(accepts(DiscontinueRequestDto.class, "reason", text))
          .as("reason %s", codePoints(text))
          .isEqualTo(notBlank);
      // A currency has exactly three characters.
      String currency = text.isEmpty() ? text : text.repeat(3).substring(0, 3);
      boolean validCurrency = accepts(Reference.class, "text", currency) && currency.length() == 3;
      assertThat(accepts(CreateProductRequestDto.class, "currency", currency))
          .as("currency of a new product %s", codePoints(currency))
          .isEqualTo(validCurrency);
      assertThat(accepts(ChangePriceRequestDto.class, "currency", currency))
          .as("currency of a new price %s", codePoints(currency))
          .isEqualTo(validCurrency);
    }
  }

  @Test
  void aPriceIsValidForTheContractExactlyWhenItIsValidForBeanValidation() {
    List<String> prices =
        List.of(
            "0",
            "0.00",
            "-0.01",
            "-1",
            "0.001",
            "0.01",
            "0.010",
            "1",
            "1.5",
            "18.90",
            "18.900",
            "18.901",
            "9999999999",
            "9999999999.99",
            "9999999999.990",
            "9999999999.999",
            "10000000000",
            "10000000000.00",
            "99999999999",
            "1E+10",
            "1E+9",
            "1E-2",
            "1E-3",
            "12.3450",
            "123456789.12",
            "0.00000000001");

    for (String price : prices) {
      BigDecimal value = new BigDecimal(price);
      boolean valid = accepts(Reference.class, "price", value);
      assertThat(accepts(CreateProductRequestDto.class, "price", value)).as(price).isEqualTo(valid);
      assertThat(accepts(ChangePriceRequestDto.class, "price", value)).as(price).isEqualTo(valid);
    }
    assertThat(accepts(CreateProductRequestDto.class, "price", null)).isFalse();
  }

  private static boolean accepts(Class<?> type, String property, Object value) {
    return VALIDATOR.validateValue(type, property, value).isEmpty();
  }

  private static String codePoints(String text) {
    return text.codePoints().mapToObj(c -> "U+%04X".formatted(c)).toList().toString();
  }
}
