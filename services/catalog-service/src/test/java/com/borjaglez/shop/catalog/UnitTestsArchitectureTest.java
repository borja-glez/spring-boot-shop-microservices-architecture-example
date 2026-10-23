package com.borjaglez.shop.catalog;

import com.borjaglez.shop.architecture.ShopArchitecture;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

/**
 * No class of the unit suite ({@code src/test}) uses Spring: the tests that need an application
 * context live in {@code src/integrationTest} (slices) and {@code src/componentTest} (the whole
 * application).
 */
@AnalyzeClasses(
    packages = "com.borjaglez.shop.catalog",
    importOptions = ImportOption.OnlyIncludeTests.class)
class UnitTestsArchitectureTest {

  @ArchTest static final ArchTests rules = ShopArchitecture.unitTests();
}
