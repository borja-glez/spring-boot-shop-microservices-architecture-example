package com.borjaglez.shop.testsupport.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture rules every service follows.
 *
 * <pre>
 * api ──────────┐
 *   │           ▼
 *   └──▶ application ──▶ domain
 *               ▲          ▲
 * infrastructure┴──────────┘
 * </pre>
 *
 * <ul>
 *   <li>{@code domain} depends on no other layer.
 *   <li>{@code application} is used only by {@code api} and {@code infrastructure}.
 *   <li>{@code api} and {@code infrastructure} are leaves nobody else depends on.
 * </ul>
 */
public final class ShopArchitectureRules {

  private ShopArchitectureRules() {}

  public static ArchRule layersOf(String basePackage) {
    return layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .withOptionalLayers(true)
        .layer("Domain")
        .definedBy(basePackage + ".domain..")
        .layer("Application")
        .definedBy(basePackage + ".application..")
        .layer("Infrastructure")
        .definedBy(basePackage + ".infrastructure..")
        .layer("Api")
        .definedBy(basePackage + ".api..")
        .whereLayer("Api")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Infrastructure")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Application")
        .mayOnlyBeAccessedByLayers("Api", "Infrastructure")
        .whereLayer("Domain")
        .mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Api");
  }

  /** Dependencies are injected through constructors so they can be final and easy to test. */
  public static ArchRule noFieldInjection() {
    return noFields()
        .should()
        .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
        .because("dependencies are injected through constructors");
  }
}
