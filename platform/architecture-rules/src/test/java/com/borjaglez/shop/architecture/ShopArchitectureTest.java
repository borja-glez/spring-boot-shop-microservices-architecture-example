package com.borjaglez.shop.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

/**
 * Applies every rule through the ArchUnit engine, as a service does, to the well-formed services.
 */
@AnalyzeClasses(
    packages = {
      "com.borjaglez.shop.fixture.good.orders",
      "com.borjaglez.shop.fixture.good.billing"
    })
class ShopArchitectureTest {

  @ArchTest static final ArchTests RULES = ShopArchitecture.all();
}
