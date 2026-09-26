package com.borjaglez.shop.eskit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.context.annotation.ImportRuntimeHints;

class EsKitRuntimeHintsTest {

  @Test
  void theEsKitMigrationsAreIncludedInNativeImages() {
    RuntimeHints hints = new RuntimeHints();
    new EsKitRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertThat(RuntimeHintsPredicates.resource().forResource("db/eskit/V1000__event_store.sql"))
        .accepts(hints);
  }

  @Test
  void hibernateMayCreateArraysOfTheIdTypes() {
    RuntimeHints hints = new RuntimeHints();
    new EsKitRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertThat(RuntimeHintsPredicates.reflection().onType(UUID[].class)).accepts(hints);
    assertThat(RuntimeHintsPredicates.reflection().onType(String[].class)).accepts(hints);
  }

  @Test
  void flywayCanTranslateConnectionErrors() {
    RuntimeHints hints = new RuntimeHints();
    new EsKitRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertThat(
            RuntimeHintsPredicates.reflection()
                .onType(
                    TypeReference.of(
                        "org.flywaydb.core.internal.exception.sqlExceptions.FlywaySqlNoIntegratedAuthException"))
                .withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS))
        .accepts(hints);
  }

  @Test
  void theAutoConfigurationContributesThem() {
    assertThat(EventStoreAutoConfiguration.class.getAnnotation(ImportRuntimeHints.class).value())
        .contains(EsKitRuntimeHints.class);
  }
}
