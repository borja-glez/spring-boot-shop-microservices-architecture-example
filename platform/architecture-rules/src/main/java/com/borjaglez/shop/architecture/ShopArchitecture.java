package com.borjaglez.shop.architecture;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

/**
 * Every architecture rule of the shop. A service applies them from its {@code ArchitectureTest}:
 *
 * <pre>{@code
 * @AnalyzeClasses(packages = "com.borjaglez.shop.orders")
 * class ArchitectureTest {
 *   @ArchTest static final ArchTests rules = ShopArchitecture.all();
 * }
 * }</pre>
 */
public final class ShopArchitecture {

  private ShopArchitecture() {}

  public static ArchTests all() {
    return ArchTests.in(AllRules.class);
  }

  /** Holds the three rule classes so they can be handed out as one {@link ArchTests}. */
  interface AllRules {

    @ArchTest ArchTests HEXAGONAL = ArchTests.in(HexagonalRules.class);

    @ArchTest ArchTests DDD = ArchTests.in(DddRules.class);

    @ArchTest ArchTests LAYOUT = ArchTests.in(LayoutRules.class);
  }
}
