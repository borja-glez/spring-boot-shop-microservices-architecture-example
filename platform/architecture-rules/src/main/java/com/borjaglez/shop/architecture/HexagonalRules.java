package com.borjaglez.shop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import java.util.Set;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Hexagonal architecture of a service: a framework-free core ({@code domain}, {@code application})
 * and adapters ({@code infrastructure.in}, {@code infrastructure.out}, {@code
 * infrastructure.config}) around it.
 *
 * <p>The rules are generic over {@code com.borjaglez.shop.<service>..} and find no classes in a
 * service that lacks a layer, such as the gateway, so they pass vacuously there.
 */
public final class HexagonalRules {

  private static final String DOMAIN = "domain";
  private static final String APPLICATION = "application";
  private static final String INFRASTRUCTURE = "infrastructure";
  private static final String INBOUND = "infrastructure.in";
  private static final String OUTBOUND = "infrastructure.out";

  /** The packages of the cqrs API an application service may use, without their sub-packages. */
  private static final Set<String> CQRS_API =
      Set.of(
          "com.borjaglez.cqrs",
          "com.borjaglez.cqrs.command",
          "com.borjaglez.cqrs.command.annotation",
          "com.borjaglez.cqrs.query",
          "com.borjaglez.cqrs.query.annotation",
          "com.borjaglez.cqrs.event",
          "com.borjaglez.cqrs.event.annotation",
          "com.borjaglez.cqrs.naming",
          "com.borjaglez.cqrs.idempotency");

  private HexagonalRules() {}

  @ArchTest
  public static final ArchRule CORE_IS_FRAMEWORK_FREE =
      noClasses()
          .that(Layers.reside(DOMAIN).or(Layers.reside(APPLICATION)))
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta..",
              "org.hibernate..",
              "com.fasterxml.jackson..",
              "tools.jackson..",
              "com.borjaglez.specrepository..",
              "com.borjaglez.shop.support..",
              "com.borjaglez.shop.eskit..",
              "org.slf4j..")
          .because("the core must run and be tested without any framework")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule DOMAIN_ONLY_KNOWS_ITSELF_AND_THE_KERNEL =
      classes()
          .that(Layers.reside(DOMAIN))
          .should(
              Layers.onlyDependOn(
                  "java, its own domain and the kernel",
                  (origin, target) ->
                      Layers.inJava(target)
                          || Layers.inKernel(target)
                          || Layers.inLayerOfSameService(origin, target, DOMAIN)))
          .because("the domain model is built from the language and the shared kernel alone")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule APPLICATION_DEPENDENCIES =
      classes()
          .that(Layers.reside(APPLICATION))
          .should(
              Layers.onlyDependOn(
                  "java, its own domain and application, the kernel, the contracts and the cqrs"
                      + " API",
                  (origin, target) ->
                      Layers.inJava(target)
                          || Layers.inKernel(target)
                          || Layers.inContracts(target)
                          || CQRS_API.contains(target.getPackageName())
                          || Layers.inLayerOfSameService(origin, target, DOMAIN)
                          || Layers.inLayerOfSameService(origin, target, APPLICATION)))
          .because("use cases orchestrate the domain through ports and the cqrs API only")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule DEPENDENCIES_POINT_INWARDS =
      noClasses()
          .that(Layers.reside(DOMAIN).or(Layers.reside(APPLICATION)))
          .should()
          .dependOnClassesThat(Layers.reside(INFRASTRUCTURE))
          .because("the core defines ports and never knows the adapters that implement them")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule INBOUND_AND_OUTBOUND_ADAPTERS_ARE_SEPARATE =
      classes()
          .that(Layers.reside(INBOUND).or(Layers.reside(OUTBOUND)))
          .should(
              Layers.notDependOn(
                  "the opposite kind of adapter",
                  (origin, target) ->
                      crosses(origin, target, INBOUND, OUTBOUND)
                          || crosses(origin, target, OUTBOUND, INBOUND)))
          .because("inbound and outbound adapters meet only through the application core")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule ADAPTERS_ARE_INDEPENDENT =
      classes()
          .that(Layers.areInAnAdapter())
          .should(
              Layers.notDependOn(
                  "another adapter",
                  (origin, target) -> {
                    var adapter = Layers.adapterOf(target);
                    return adapter.isPresent() && !adapter.equals(Layers.adapterOf(origin));
                  }))
          .because("every adapter can be replaced without touching the others")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule NO_FIELD_INJECTION =
      noFields()
          .should()
          .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
          .orShould()
          .beAnnotatedWith("jakarta.inject.Inject")
          .orShould()
          .beAnnotatedWith("org.springframework.beans.factory.annotation.Value")
          .orShould()
          .beAnnotatedWith("jakarta.annotation.Resource")
          .orShould()
          .beAnnotatedWith("jakarta.persistence.PersistenceContext")
          .because("dependencies are injected through constructors so they can be final")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule SERVICES_ARE_ISOLATED =
      classes()
          .that(Layers.areInAService())
          .should(
              Layers.notDependOn(
                  "another service",
                  (origin, target) ->
                      Layers.locate(target)
                          .map(
                              location ->
                                  !location
                                      .service()
                                      .equals(Layers.locate(origin).orElseThrow().service()))
                          .orElse(false)))
          .because("services talk through contracts and messages, never through each other's code");

  private static boolean crosses(JavaClass origin, JavaClass target, String from, String to) {
    return Layers.inLayer(origin, from) && Layers.inLayer(target, to);
  }
}
