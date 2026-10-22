package com.borjaglez.shop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Set;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Where each kind of class lives in a service. */
public final class LayoutRules {

  private static final String EVENT_HANDLER = "com.borjaglez.cqrs.event.annotation.EventHandler";
  private static final Set<String> TEST_ANNOTATIONS =
      Set.of(
          "org.junit.jupiter.api.Test",
          "org.junit.jupiter.api.RepeatedTest",
          "org.junit.jupiter.api.TestFactory",
          "org.junit.jupiter.api.TestTemplate",
          "org.junit.jupiter.params.ParameterizedTest");

  private LayoutRules() {}

  @ArchTest
  public static final ArchRule HANDLERS_LIVE_IN_APPLICATION_SERVICES =
      classes()
          .that()
          .areAnnotatedWith("com.borjaglez.cqrs.command.annotation.CommandHandler")
          .or()
          .areAnnotatedWith("com.borjaglez.cqrs.query.annotation.QueryHandler")
          .or()
          .areAnnotatedWith(EVENT_HANDLER)
          .should(
              Layers.satisfy(
                  "live in application.service (event handlers also in infrastructure.in.messaging)",
                  javaClass ->
                      Layers.inLayer(javaClass, "application.service")
                          || (javaClass.isAnnotatedWith(EVENT_HANDLER)
                              && Layers.inLayer(javaClass, "infrastructure.in.messaging"))))
          .because("handlers are the use cases of the application core")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule CONTROLLERS_LIVE_IN_REST_ADAPTERS =
      classes()
          .that()
          .areMetaAnnotatedWith("org.springframework.stereotype.Controller")
          .should(
              Layers.satisfy(
                  "live in infrastructure.in.rest",
                  javaClass -> Layers.inLayer(javaClass, "infrastructure.in.rest")))
          .because("HTTP is one inbound adapter among others")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule ENTITIES_LIVE_IN_PERSISTENCE_ADAPTERS =
      classes()
          .that()
          .areAnnotatedWith("jakarta.persistence.Entity")
          .should(
              Layers.satisfy(
                  "live in infrastructure.out.persistence",
                  javaClass -> Layers.inLayer(javaClass, "infrastructure.out.persistence")))
          .because("persistence mappings are an outbound adapter detail")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule CONFIGURATION_LIVES_IN_CONFIG =
      classes()
          .that()
          .areAnnotatedWith("org.springframework.context.annotation.Configuration")
          .should(
              Layers.satisfy(
                  "live in infrastructure.config",
                  javaClass -> Layers.inLayer(javaClass, "infrastructure.config")))
          .because("wiring is kept in one place, away from the core and the adapters")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule OUTBOUND_PORTS_ARE_INTERFACES_OR_RECORDS =
      classes()
          .that(Layers.reside("application.port.out"))
          .should(
              Layers.satisfy(
                  "be an interface or a record",
                  javaClass -> javaClass.isInterface() || javaClass.isRecord()))
          .because("an outbound port is a contract, with records for the values it exchanges")
          .allowEmptyShould(true);

  /**
   * Unit tests run without a Spring context. Meant for the {@code ArchitectureTest} of the {@code
   * test} suite, imported with {@code ImportOption.OnlyIncludeTests}; the rule only looks at
   * classes that declare tests, so it passes vacuously when applied to production classes.
   */
  @ArchTest
  public static final ArchRule UNIT_TESTS_DO_NOT_USE_SPRING =
      noClasses()
          .that(
              DescribedPredicate.describe(
                  "declare tests",
                  (JavaClass javaClass) ->
                      javaClass.getMethods().stream()
                          .anyMatch(
                              method ->
                                  TEST_ANNOTATIONS.stream().anyMatch(method::isAnnotatedWith))))
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework..")
          .because("unit tests are fast because they start no Spring context")
          .allowEmptyShould(true);
}
