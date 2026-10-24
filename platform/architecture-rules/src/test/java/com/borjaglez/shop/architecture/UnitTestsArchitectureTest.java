package com.borjaglez.shop.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

/**
 * The pattern of a service: its test classes, imported on their own, are checked by unitTests().
 */
@AnalyzeClasses(
    packages = "com.borjaglez.shop.fixture.good.unit",
    importOptions = ImportOption.OnlyIncludeTests.class)
class UnitTestsArchitectureTest {

  @ArchTest static final ArchTests RULES = ShopArchitecture.unitTests();
}
