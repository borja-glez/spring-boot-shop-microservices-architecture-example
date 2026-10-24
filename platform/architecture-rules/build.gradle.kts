plugins {
    `java-library`
    // No Spring Boot BOM: the rules are applied by Boot 3 and Boot 4 services alike.
    id("shop.java-base-conventions")
    id("shop.coverage-conventions")
}

description = "ArchUnit rules for hexagonal architecture, DDD and package layout shared by every service."

dependencies {
    // ArchRule and the DSL; archunit-junit5-api adds ArchTest/ArchTests (and brings archunit) without the engine.
    api(libs.archunit)
    api(libs.archunit.junit5.api)

    // Framework types are referenced by name in the rules; the fixtures need the real annotations.
    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)
    testImplementation(project(":platform:domain-kernel"))
    testImplementation(libs.cqrs.api)
    testImplementation(libs.spring.webmvc)
    testImplementation(libs.spring.boot.test)
    testImplementation(libs.jakarta.persistence.api)
    testImplementation(libs.jakarta.inject.api)
    testImplementation(libs.jakarta.annotation.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}
