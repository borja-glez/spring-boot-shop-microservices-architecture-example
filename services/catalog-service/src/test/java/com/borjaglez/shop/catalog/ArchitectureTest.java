package com.borjaglez.shop.catalog;

import com.borjaglez.cqrs.test.architecture.CqrsArchitectureRules;
import com.borjaglez.shop.architecture.ShopArchitecture;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The production classes of the catalog follow the shop's hexagonal, DDD and layout rules, and the
 * rules of spring-boot-cqrs for the code of a service: framework-free handlers, handler methods in
 * handler classes and plain-data messages. The commands and queries of the catalog never leave it,
 * so they carry no {@code @CqrsMessage} and keep no constructor without arguments; the messages
 * that cross services are checked in {@code platform/contracts}.
 */
@AnalyzeClasses(
    packages = "com.borjaglez.shop.catalog",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest static final ArchTests shop = ShopArchitecture.all();

  @ArchTest
  static final ArchRule handlersDoNotDependOnSpring =
      CqrsArchitectureRules.handlersDoNotDependOnSpring();

  @ArchTest
  static final ArchRule handlerMethodsAreInHandlerClasses =
      CqrsArchitectureRules.handlerMethodsAreInHandlerClasses();

  @ArchTest
  static final ArchRule messagesDoNotDependOnFrameworks =
      CqrsArchitectureRules.messagesDoNotDependOnFrameworks();
}
