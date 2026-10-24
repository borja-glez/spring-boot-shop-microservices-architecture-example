package com.borjaglez.shop.support.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.context.annotation.ImportRuntimeHints;

import com.borjaglez.shop.support.web.problem.MappedProblem.FieldViolation;

class ShopWebRuntimeHintsTest {

  @Test
  void jacksonCanReadTheComponentsOfAFieldViolation() {
    RuntimeHints hints = new RuntimeHints();
    new ShopWebRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertThat(RuntimeHintsPredicates.reflection().onType(FieldViolation.class)).accepts(hints);
    assertThat(
            RuntimeHintsPredicates.reflection().onMethodInvocation(FieldViolation.class, "field"))
        .accepts(hints);
    assertThat(
            RuntimeHintsPredicates.reflection().onMethodInvocation(FieldViolation.class, "message"))
        .accepts(hints);
  }

  @Test
  void theAutoConfigurationContributesThem() {
    assertThat(ShopWebAutoConfiguration.class.getAnnotation(ImportRuntimeHints.class).value())
        .contains(ShopWebRuntimeHints.class);
  }
}
