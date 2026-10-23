package com.borjaglez.shop.contracts;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.borjaglez.cqrs.test.architecture.CqrsArchitectureRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The contracts cross process boundaries and are read by services running different Spring Boot and
 * Jackson versions. Every message keeps the name the transports route it by, a constructor the
 * deserializers create it with, and no framework type: the rules spring-boot-cqrs gives for the
 * messages that cross a service boundary, while each service checks its own code with the rest.
 * They fail when they find no message, so a wrong package cannot pass. On top of them, no class of
 * the module, messages, values and replies alike, depends on a framework.
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

  /**
   * Boot 3 services read the contracts with Spring 6 and Jackson 2, Boot 4 services with Spring 7
   * and Jackson 3: any Spring, Jackson (annotations included), JPA or Hibernate type in a contract,
   * such as the {@code StockLevel} values of a reply, would tie it to one of them.
   */
  @ArchTest
  static final ArchRule contractsCanBeSharedByBoot3AndBoot4Services =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "com.fasterxml.jackson..",
              "tools.jackson..",
              "jakarta.persistence..",
              "org.hibernate..")
          .because("Boot 3 and Boot 4 services read the same contracts");
}
