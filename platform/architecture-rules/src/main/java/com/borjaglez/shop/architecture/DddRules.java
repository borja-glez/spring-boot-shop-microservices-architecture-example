package com.borjaglez.shop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/** Domain-driven design conventions of the domain model. */
public final class DddRules {

  private static final String DOMAIN_EVENT = "com.borjaglez.shop.kernel.domain.DomainEvent";
  private static final String AGGREGATE_ROOT = "com.borjaglez.shop.kernel.domain.AggregateRoot";
  private static final String EVENT_SOURCED_AGGREGATE =
      "com.borjaglez.shop.kernel.domain.EventSourcedAggregate";
  private static final String DOMAIN_EXCEPTION = "com.borjaglez.shop.kernel.error.DomainException";

  private DddRules() {}

  @ArchTest
  public static final ArchRule DOMAIN_EVENTS_ARE_RECORDS =
      classes()
          .that(Layers.areInAService())
          .and()
          .areAssignableTo(DOMAIN_EVENT)
          .and()
          .areNotInterfaces()
          .should()
          .beRecords()
          .because("a domain event is an immutable fact, which a record states by construction")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule EVENT_FAMILIES_ARE_SEALED =
      classes()
          .that(Layers.areInAService())
          .and()
          .areInterfaces()
          .and()
          .areAssignableTo(DOMAIN_EVENT)
          .should(Layers.satisfy("be sealed", JavaClass::isSealed))
          .because("sealing a family of events lets switches over them be exhaustive")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule NO_PUBLIC_SETTERS_IN_THE_DOMAIN =
      noMethods()
          .that()
          .areDeclaredInClassesThat(Layers.reside("domain"))
          .and()
          .arePublic()
          .should()
          .haveNameMatching("set[A-Z].*")
          .because("the domain changes state through behavior, not through setters")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule NO_PUBLIC_MUTABLE_FIELDS_IN_THE_DOMAIN =
      fields()
          .that()
          .areDeclaredInClassesThat(Layers.reside("domain"))
          .should()
          .notBePublic()
          .orShould()
          .beFinal()
          .because("the domain protects its state behind its behavior")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule REPOSITORIES_SAVE_AGGREGATE_ROOTS =
      methods()
          .that()
          .areDeclaredInClassesThat(
              Layers.reside("domain")
                  .and(JavaClass.Predicates.INTERFACES)
                  .and(
                      DescribedPredicate.describe(
                          "are named *Repository",
                          (JavaClass javaClass) ->
                              javaClass.getSimpleName().endsWith("Repository"))))
          .and()
          .haveName("save")
          .should(
              new ArchCondition<JavaMethod>("receive an aggregate root") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                  boolean receivesAggregate =
                      method.getRawParameterTypes().stream()
                          .anyMatch(
                              type ->
                                  type.isAssignableTo(AGGREGATE_ROOT)
                                      || type.isAssignableTo(EVENT_SOURCED_AGGREGATE));
                  events.add(
                      new SimpleConditionEvent(
                          method,
                          receivesAggregate,
                          method.getFullName() + " receives no aggregate root"));
                }
              })
          .because("repositories persist whole aggregates, never their parts")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule DOMAIN_EXCEPTIONS_EXTEND_THE_KERNEL =
      classes()
          .that(Layers.reside("domain.exception").or(Layers.reside("application.exception")))
          .should()
          .beAssignableTo(DOMAIN_EXCEPTION)
          .because("the web layer maps the kernel's exceptions to problem responses")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule THE_DOMAIN_DOES_NOT_SEE_CONTRACTS =
      noClasses()
          .that(Layers.reside("domain"))
          .should()
          .dependOnClassesThat()
          .resideInAPackage(Layers.CONTRACTS + "..")
          .because("contracts are the public messages of a service, not part of its model")
          .allowEmptyShould(true);
}
