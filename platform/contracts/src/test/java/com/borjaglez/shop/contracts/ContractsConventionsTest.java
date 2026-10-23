package com.borjaglez.shop.contracts;

import com.borjaglez.cqrs.test.architecture.CqrsArchitectureRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Every message of the contracts crosses a process boundary and is read by services running
 * different Spring Boot and Jackson versions, so each one keeps the name the transports route it
 * by, a constructor the deserializers create it with, and no framework type. These are the rules
 * spring-boot-cqrs gives for the messages that cross a service boundary; each service checks its
 * own code with the rest. They fail when they find no message, so a wrong package cannot pass.
 */
@AnalyzeClasses(
    packages = "com.borjaglez.shop.contracts",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ContractsConventionsTest {

  @ArchTest
  static final ArchRule messagesDeclareTheirWireName =
      CqrsArchitectureRules.messagesAreAnnotatedWithCqrsMessage();

  @ArchTest
  static final ArchRule messagesCanBeInstantiatedByDeserializers =
      CqrsArchitectureRules.messagesHaveANoArgConstructor();

  @ArchTest
  static final ArchRule messagesDoNotDependOnFrameworks =
      CqrsArchitectureRules.messagesDoNotDependOnFrameworks();
}
