package com.borjaglez.shop.reporting;

import com.borjaglez.shop.testsupport.architecture.ShopArchitectureRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.borjaglez.shop.reporting",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule layers = ShopArchitectureRules.layersOf("com.borjaglez.shop.reporting");

  @ArchTest static final ArchRule constructorInjection = ShopArchitectureRules.noFieldInjection();
}
