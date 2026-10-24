package com.borjaglez.shop.architecture;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The architecture rules of the shop. A service applies them from two classes of its {@code test}
 * suite, one per set of classes, because the production and the test classes are imported
 * separately:
 *
 * <pre>{@code
 * @AnalyzeClasses(
 *     packages = "com.borjaglez.shop.orders",
 *     importOptions = ImportOption.DoNotIncludeTests.class)
 * class ArchitectureTest {
 *   @ArchTest static final ArchTests rules = ShopArchitecture.all();
 * }
 *
 * @AnalyzeClasses(
 *     packages = "com.borjaglez.shop.orders",
 *     importOptions = ImportOption.OnlyIncludeTests.class)
 * class UnitTestsArchitectureTest {
 *   @ArchTest static final ArchTests rules = ShopArchitecture.unitTests();
 * }
 * }</pre>
 */
public final class ShopArchitecture {

  private ShopArchitecture() {}

  /**
   * The rules of the production classes (import them with {@code ImportOption.DoNotIncludeTests}).
   * Apart from the hexagonal and layout rules that find nothing in a service without that layer,
   * they fail on an empty import, so a mistyped package cannot pass.
   */
  public static ArchTests all() {
    return ArchTests.in(ProductionRules.class);
  }

  /**
   * The rule of the unit tests (import them with {@code ImportOption.OnlyIncludeTests}): no class
   * of the {@code test} suite depends on Spring.
   */
  public static ArchTests unitTests() {
    return ArchTests.in(UnitTestRules.class);
  }

  /** Holds the three rule classes so they can be handed out as one {@link ArchTests}. */
  interface ProductionRules {

    @ArchTest ArchTests HEXAGONAL = ArchTests.in(HexagonalRules.class);

    @ArchTest ArchTests DDD = ArchTests.in(DddRules.class);

    @ArchTest ArchTests LAYOUT = ArchTests.in(LayoutRules.class);
  }

  interface UnitTestRules {

    @ArchTest ArchRule UNIT_TESTS_DO_NOT_USE_SPRING = LayoutRules.UNIT_TESTS_DO_NOT_USE_SPRING;
  }
}
