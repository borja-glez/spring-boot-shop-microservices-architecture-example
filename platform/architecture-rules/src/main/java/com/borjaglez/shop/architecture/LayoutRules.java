package com.borjaglez.shop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Where each kind of class lives in a service. */
public final class LayoutRules {

  private static final String EVENT_HANDLER = "com.borjaglez.cqrs.event.annotation.EventHandler";
  private static final String DOMAIN_EXCEPTION = "com.borjaglez.shop.kernel.error.DomainException";

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
          .or()
          .areAnnotatedWith("jakarta.persistence.Embeddable")
          .or()
          .areAnnotatedWith("jakarta.persistence.MappedSuperclass")
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
                  "be an interface, a record or an enum",
                  javaClass ->
                      javaClass.isInterface() || javaClass.isRecord() || javaClass.isEnum()))
          .because(
              "an outbound port is a contract, with records and enums for the values it exchanges")
          .allowEmptyShould(true);

  /**
   * The name of every domain exception of a service ends in {@code Exception}, as the kernel's do
   * ({@code NotFoundException}, {@code ConflictException}...), whichever layer raises it.
   */
  @ArchTest
  public static final ArchRule DOMAIN_EXCEPTIONS_ARE_NAMED_EXCEPTION =
      classes()
          .that(Layers.areInAService())
          .and()
          .areAssignableTo(DOMAIN_EXCEPTION)
          .should()
          .haveSimpleNameEndingWith("Exception")
          .because("one suffix names every failure, in the domain and in the application alike")
          .allowEmptyShould(true);

  /**
   * Every class of the {@code test} suite runs without a Spring context. Apply it to the classes
   * imported with {@code ImportOption.OnlyIncludeTests} through {@link
   * ShopArchitecture#unitTests()}; it is not part of {@link ShopArchitecture#all()}.
   */
  public static final ArchRule UNIT_TESTS_DO_NOT_USE_SPRING =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework..")
          .because("unit tests are fast because they start no Spring context");
}
