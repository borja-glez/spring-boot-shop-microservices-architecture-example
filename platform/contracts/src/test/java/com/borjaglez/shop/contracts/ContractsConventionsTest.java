package com.borjaglez.shop.contracts;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Every message crosses a process boundary and is deserialized by services running different Spring
 * Boot and Jackson versions, so contracts must stay annotated, deserializable and free of framework
 * types.
 */
class ContractsConventionsTest {

  private static JavaClasses contracts;

  @BeforeAll
  static void importContracts() {
    contracts =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.borjaglez.shop.contracts");
  }

  private static final DescribedPredicate<JavaClass> MESSAGES =
      DescribedPredicate.describe(
          "messages",
          type ->
              !type.getModifiers().contains(JavaModifier.ABSTRACT)
                  && (type.isAssignableTo(Event.class)
                      || type.isAssignableTo(Command.class)
                      || type.isAssignableTo(Query.class)));

  @Test
  void thereAreContractsToCheck() {
    classes().that(MESSAGES).should().bePublic().check(contracts);
  }

  @Test
  void messagesDeclareTheirWireName() {
    classes().that(MESSAGES).should().beAnnotatedWith(CqrsMessage.class).check(contracts);
  }

  @Test
  void messagesCanBeInstantiatedByDeserializers() {
    classes().that(MESSAGES).should(haveNoArgConstructor()).check(contracts);
  }

  @Test
  void contractsDoNotDependOnFrameworks() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "com.fasterxml.jackson..",
            "tools.jackson..",
            "jakarta.persistence..")
        .check(contracts);
  }

  private static ArchCondition<JavaClass> haveNoArgConstructor() {
    return new ArchCondition<>("have a no-arg constructor") {
      @Override
      public void check(JavaClass type, ConditionEvents events) {
        boolean present =
            type.getConstructors().stream().anyMatch(c -> c.getRawParameterTypes().isEmpty());
        events.add(
            new SimpleConditionEvent(
                type, present, type.getName() + (present ? " has" : " lacks") + " one"));
      }
    };
  }
}
